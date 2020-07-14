(ns status-im.ui.components.invite.accept
  (:require [re-frame.core :as re-frame]
            [status-im.ethereum.tokens :as tokens]
            [quo.react-native :as rn]
            [quo.core :as quo]
            [status-im.i18n :as i18n]
            [status-im.ui.components.invite.events :as invite]
            [status-im.ui.components.colors :as colors]
            [status-im.acquisition.core :as acquisition]))

(defn perk [{name             :name
             {source :source} :icon} value]
  [rn/view {:style {:flex-direction   :row
                    :align-items      :center
                    :padding-vertical 4}}
   [rn/view {:style {:flex-direction :row}}
    [rn/image {:source (if (fn? source) (source) source)
               :style  {:width        20
                        :height       20
                        :margin-right 8}}]
    [quo/text {:size   :small
               :weight :medium}
     (str value " ")]
    [quo/text {:size   :small
               :weight :medium}
     name]]])

(defn accept-popover []
  (fn []
    (let [starter-pack-amount @(re-frame/subscribe [::acquisition/starter-pack])
          all-tokens          @(re-frame/subscribe [:wallet/all-tokens])
          tokens              (->> (get starter-pack-amount :tokens)
                                   (map #(tokens/address->token all-tokens %))
                                   ;; TODO: Addatch ETH
                                   (mapv (fn [v i k] [k v i])
                                         (get starter-pack-amount :tokens-amount)
                                         (range)))]
     [rn/view
      [rn/view {:style {:align-items        :center
                        :padding-vertical   16
                        :padding-horizontal 16}}
       [rn/view {:flex-direction :row
                 :padding-left   20
                 :height         40}
        (for [[{name             :name
                {source :source} :icon} _ i] tokens]
          ^{:key name}
          [rn/image {:source (if (fn? source) (source) source)
                     :style  {:width  40
                              :height 40
                              :left   (* i -20)}}])]
       [rn/view {:style {:padding 8}}
        [quo/text {:style {:margin-bottom 8}
                   :align :center
                   :size  :x-large}
         (i18n/label :t/advertiser-starter-pack-title)]
        [quo/text {:align :center}
         (i18n/label :t/advertiser-starter-pack-description)]]
       [rn/view {:style {:border-radius      8
                         :border-width       1
                         :border-color       colors/gray-lighter
                         :width              "100%"
                         :margin-vertical    8
                         :padding-vertical   8
                         :padding-horizontal 12}}
        (for [[k v] tokens]
          ^{:key (:name k)}
          [perk k v])]
       [rn/view {:style {:margin-vertical 8}}
        [quo/button {:on-press #(re-frame/dispatch [::acquisition/advertiser-decision :accept])}
         (i18n/label :t/advertiser-starter-pack-accept)]]
       [quo/button {:type     :secondary
                    :on-press #(re-frame/dispatch [::acquisition/advertiser-decision :decline])}
        (i18n/label :t/advertiser-starter-pack-decline)]
       [rn/view {:padding-vertical 8}
        [quo/text {:color :secondary
                   :align :center
                   :size  :small}
         (i18n/label :t/invite-privacy-policy1)
         [quo/text {:color    :link
                    :on-press #(re-frame/dispatch [::invite/terms-and-conditions])}
          (i18n/label :t/invite-privacy-policy2)]]]]])))
