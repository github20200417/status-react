(ns status-im.acquisition.core
  (:require [re-frame.core :as re-frame]
            [reagent.ratom :refer [make-reaction]]
            [status-im.utils.fx :as fx]
            [status-im.ethereum.json-rpc :as json-rpc]
            [status-im.popover.core :as popover]
            [status-im.waku.core :as waku]
            [status-im.utils.types :as types]
            [status-im.utils.platform :as platform]
            [status-im.ethereum.core :as ethereum]
            [status-im.ethereum.contracts :as contracts]
            [status-im.utils.money :as money]
            [taoensso.timbre :as log]
            ["react-native-device-info" :refer [getInstallReferrer]]
            ["@react-native-community/async-storage" :default async-storage]))

(def advertiser-type "advertiser")

(def acquisition-gateway "https://test-referral.status.im")

(def acquisition-routes {:clicks        (str acquisition-gateway "/clicks")
                         :registrations (str acquisition-gateway "/registrations")})

(defn get-url [type referral]
  (if (= type :clicks)
    (str (get acquisition-routes :clicks) "/" referral)
    (get acquisition-routes :registrations)))

(def referrer-decision-key "referrer-decision")

(fx/defn handle-error
  {:events [::on-error]}
  [_ error]
  {:utils/show-popup {:title   "Request failed"
                      :content (str error)}})

(fx/defn handle-acquisition
  {:events [::handle-acquisition]}
  [{:keys [db] :as cofx} {:keys [message on-success method url]}]
  (let [msg (types/clj->json message)]
    {::json-rpc/call [{:method     (json-rpc/call-ext-method (waku/enabled? cofx) "signMessageWithChatKey")
                       :params     [msg]
                       :on-error   #(re-frame/dispatch [::on-error "Could not sign message"])
                       :on-success #(re-frame/dispatch [::call-acquisition-gateway
                                                        {:chat-key   (get-in db [:multiaccount :public-key])
                                                         :message    msg
                                                         :method     method
                                                         :url        url
                                                         :on-success on-success} %])}]}))

(fx/defn handle-registration
  [cofx {:keys [message on-success]}]
  (handle-acquisition cofx {:message    message
                            :on-success on-success
                            :method     "POST"
                            :url        (get-url :registrations nil)}))

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
                (when (nil? data)
                  (-> (getInstallReferrer)
                      (.then (fn [_]
                               (re-frame/dispatch [::has-referrer data "29b5740a317a4b1a9b97" ])))))))
       (.catch (fn [error]
                 (log/error "[async-storage]" error))))))

(fx/defn referrer-registered
  {:events [::referrer-registered]}
  [{:keys [db] :as cofx} referrer {:keys [type attributed] :as referrer-meta}]
  (when-not attributed
    (fx/merge cofx
              {:db (assoc-in db [:acquisition :metadata] referrer-meta)}
              (when (= type advertiser-type)
                (popover/show-popover {:prevent-closing? true
                                       :view             :accept-invite})))))

(fx/defn success-advertiser-claim
  {:events [::success-advertiser-claim]}
  [_]
  {::set-referrer-decision "accept"})

(fx/defn advertiser-decide
  {:events [::advertiser-decision]}
  [{:keys [db] :as cofx} decision]
  (let [referral (get-in db [:acquisition :referrer])
        payload  {:chat_key    (get-in db [:multiaccount :public-key])
                  :address     (ethereum/default-address db)
                  :invite_code referral}]
    (fx/merge cofx
              (if (= decision :accept)
                (handle-acquisition {:message    payload
                                     :method     "PATCH"
                                     :url        (get-url :clicks referral)
                                     :on-success ::success-advertiser-claim})
                {::set-referrer-decision "decline"})
              (popover/hide-popover))))

(fx/defn has-referrer
  {:events [::has-referrer]}
  [{:keys [db]} decision referrer]
  (when (and referrer (nil? decision))
    {:db       (assoc-in db [:acquisition :referrer] referrer)
     :http-get {:url                   (get-url :clicks referrer)
                :success-event-creator (fn [response]
                                         [::referrer-registered referrer (types/json->clj response)])
                :failure-event-creator (fn [error]
                                         [::on-error (types/json->clj error)])}}))

(fx/defn app-setup
  {}
  [cofx]
  (fx/merge cofx {::get-referrer nil}
            (popover/show-popover {:prevent-closing? true
                                   :view             :accept-invite})))

(fx/defn call-acquisition-gateway
  {:events [::call-acquisition-gateway]}
  [cofx
   {:keys [chat-key message on-success type url method] :as kek}
   sig]
  (let [payload {:chat_key chat-key
                 :msg      message
                 :sig      sig
                 :version  2}]
    {:http-post {:url                   url
                 :opts                  {:headers {"Content-Type" "application/json"}
                                         :method  method}
                 :data                  (types/clj->json payload)
                 :success-event-creator (fn [response]
                                          [on-success (types/json->clj (get response :response-body))])
                 :failure-event-creator (fn [error]
                                          [::on-error (types/json->clj (get error :response-body))])}}))
;; Starter pack

(fx/defn get-starter-pack-amount
  {:events [::starter-pack-amount]}
  [{:keys [db]} [_ eth-amount tokens tokens-amount sticker-packs]]
  ;; TODO: Fetch all tokens names and symbols
  {:db (assoc-in db [:acquisition :starter-pack :pack]
                 {:eth-amount    (money/wei->ether eth-amount)
                  :tokens        tokens
                  :tokens-amount (mapv money/wei->ether tokens-amount)
                  :sticker-packs sticker-packs})})

(fx/defn starter-pack
  {:events [::starter-pack]}
  [{:keys [db]}]
  (let [contract (contracts/get-address db :status/acquisition)]
    {::json-rpc/eth-call [{:contract   contract
                           :method     "getPack()"
                           :outputs    ["address" "uint256" "address[]" "uint256[]" "uint256[]"]
                           :on-success #(re-frame/dispatch [::starter-pack-amount (vec %)])}]}))

(re-frame/reg-sub-raw
 ::starter-pack
 (fn [db]
   (re-frame/dispatch [::starter-pack])
   (make-reaction
    (fn []
      (get-in @db [:acquisition :starter-pack :pack])))))
