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

;; {"inputs":[],"name":"getPack","outputs":[{"internalType":"address","name":"stickerMarket","type":"address"},{"internalType":"uint256","name":"ethAmount","type":"uint256"},{"internalType":"address[]","name":"tokens","type":"address[]"},{"internalType":"uint256[]","name":"tokenAmounts","type":"uint256[]"},{"internalType":"uint256[]","name":"stickerPackIds","type":"uint256[]"}],"stateMutability":"view","type":"function"

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
                                                         :method     "POST"
                                                         :url        (get-url :registrations nil)
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
                (re-frame/dispatch [::has-referrer data "test-id"])
                (when-not data
                  (-> (getInstallReferrer)
                      (.then (fn [referrer]
                               (re-frame/dispatch [::has-referrer data referrer])))))))
       (.catch (fn [error]
                 (log/error "[async-storage]" error))))))

(fx/defn referrer-registered
  {:events [::referrer-registered]}
  [cofx {:keys [type]}]
  (when true ;; (= type "advertiser")
    (popover/show-popover cofx {:prevent-closing? true
                                :view             :accept-invite})))

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
              (popover/hide-popover)
              (if (= decision :accept)
                (handle-acquisition {:message    payload
                                     :method     "PATCH"
                                     :type       (get-url :clicks referral)
                                     :on-success ::referrer-registered})
                {::set-referrer-decision "decline"}))))

(fx/defn has-referrer
  {:events [::has-referrer]}
  [{:keys [db] :as cofx} decision referrer]
  (when (and referrer (nil? decision))
    {:http-get {:url                   (get-url :clicks referrer)
                :success-event-creator (fn [response]
                                         [::referrer-registered referrer response])}}))

(fx/defn app-setup
  {}
  [cofx]
  (fx/merge cofx
            {::get-referrer nil}
            (referrer-registered nil)
            ))

(fx/defn call-acquisition-gateway
  {:events [::call-acquisition-gateway]}
  [cofx
   {:keys [chat-key message on-success type url method]}
   sig]
  (let [payload {:chat_key chat-key
                 :msg      message
                 :sig      sig
                 :version  2}]
    {:http-post {:url                   url
                 :method                method
                 :opts                  {:headers {"Content-Type" "application/json"}}
                 :data                  (types/clj->json payload)
                 :success-event-creator (fn [response]
                                          [on-success (types/json->clj (get response :response-body))])
                 :failure-event-creator (fn [error]
                                          [::on-error (types/json->clj error)])}}))
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
