(ns status-im.acquisition.core
  (:require [re-frame.core :as re-frame]
            [status-im.utils.fx :as fx]
            [status-im.ethereum.json-rpc :as json-rpc]
            [status-im.popover.core :as popover]
            [status-im.waku.core :as waku]
            [status-im.utils.types :as types]
            [status-im.utils.platform :as platform]
            [status-im.ethereum.core :as ethereum]
            [taoensso.timbre :as log]
            ["react-native-device-info" :refer [getInstallReferrer]]
            ["@react-native-community/async-storage" :default async-storage]))

(def acquisition-gateway "https://get.status.im")

(def acquisition-routes {:clicks        (str acquisition-gateway "/clicks")
                         :registrations (str acquisition-gateway "/registrations")})

(defn get-url [type referral]
  (when (= type :clicks)
    (str (get acquisition-routes :clicks) "/" referral)
    (get acquisition-routes :registrations)))

(def referrer-decision-key "referrer-decision")

(fx/defn handle-error
  {:events [::on-error]}
  [_ error]
  (prn error)
  {:utils/show-popup {:title   "Request failed"
                      :content (str error)}})

(fx/defn handle-acquisition
  {:events [::handle-acquisition]}
  [{:keys [db] :as cofx} {:keys [message on-success]}]
  (let [msg (types/clj->json message)]
    {::json-rpc/call [{:method     (json-rpc/call-ext-method (waku/enabled? cofx) "signMessageWithChatKey")
                       :params     [msg]
                       :on-error   #(re-frame/dispatch [::on-error "Could not sign message"])
                       :on-success #(re-frame/dispatch [::call-acquisition-gateway
                                                        {:chat-key   (get-in db [:multiaccount :public-key])
                                                         :message    msg
                                                         :on-success on-success} %])}]}))

(re-frame/reg-fx
 ::set-referrer-decision
 (fn [decision]
   (-> ^js async-storage
       (.setItem referrer-decision-key decision)
       (.catch (fn [error]
                 (log/error "[async-storage]" error))))))

(re-frame/reg-fx
 ::get-referrer
 (fn []
   (when platform/android? nil)
   (-> ^js async-storage
       (.getItem referrer-decision-key)
       (.then (fn [^js data]
                (println data)
                (-> (getInstallReferrer)
                    (.then (fn [referrer]
                             (re-frame/dispatch [::has-referrer data referrer]))))))
       (.catch (fn [error]
                 (log/error "[async-storage]" error))))))


(fx/defn referrer-registered
  {:events [::referrer-registered]}
  [cofx {:keys [type]}]
  (when (= type "advertiser")
    (popover/show-popover cofx {:prevent-closing? true
                                :view             :accept-invite})))

(fx/defn success-advertiser-claim
  {:events [::success-advertiser-claim]}
  [_]
  {::set-referrer-decision "accept"})

(fx/defn advertiser-decide
  {:events [::advertiser-decide]}
  [{:keys [db] :as cofx} decision]
  (let [payload {:chat_key    (get-in db [:multiaccount :public-key])
                 :address     (ethereum/default-address db)
                 :invite_code referrer}]
   (if  (= decision :accept)
     (handle-acquisition cofx {:message    payload
                               :type       :clicks
                               :on-success ::referrer-registered})
     {::set-referrer-decision "decline"})))


(fx/defn has-referrer
  {:events [::has-referrer]}
  [{:keys [db] :as cofx} decision referrer]
  (when (nil? decision)
    {:http-get {:url                   (get-url :clicks referrer)
                :success-event-creator (fn [response]
                                         (println response)
                                         [::referrer-registered response])}}))

(fx/defn app-setup
  {}
  [_]
  {::get-referrer nil})

(fx/defn call-acquisition-gateway
  {:events [::call-acquisition-gateway]}
  [cofx
   {:keys [chat-key message on-success type]
    :or   {type :registrations}}
   sig]
  (let [payload {:chat_key chat-key
                 :msg      message
                 :sig      sig
                 :version  2}
        referral (get message :invite_code)]
    {:http-post {:url                   (get-url acquisition-routes type refferal)
                 :opts                  {:headers {"Content-Type" "application/json"}}
                 :data                  (types/clj->json payload)
                 :success-event-creator (fn [response]
                                          [on-success (types/json->clj (get response :response-body))])
                 :failure-event-creator (fn [error]
                                          [::on-error (types/json->clj error)])}}))
