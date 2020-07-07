(ns status-im.ui.components.invite.accept
  (:require [re-frame.core :as re-frame]
            [quo.react-native :as rn]
            [quo.core :as quo]
            [status-im.i18n :as i18n]
            [status-im.ui.components.invite.events :as invite]
            [status-im.ui.components.colors :as colors]
            [status-im.react-native.resources :as resources]
            [status-im.acquisition.core :as acquisition]))

(defn perk [{:keys [image name value]}]
  [rn/view {:style {:flex-direction   :row
                    :align-items      :center
                    :padding-vertical 4
                    :justify-content  :space-between}}
   [rn/view {:style {:flex-direction :row}}
    [rn/image {:source (resources/get-image image)
               :style  {:width        20
                        :height       20
                        :margin-right 6}}]
    [rn/text {:style {:font-size 13}} name]]
   [rn/text {:style {:font-size 13}} (str value)]])

(defn accept-popover []
  (fn []
    (let [starter-pack-amount @(re-frame/subscribe [::invite/default-reward])]
      [rn/view
       [rn/view {:style {:align-items        :center
                         :padding-vertical   8
                         :padding-horizontal 16}}
        [rn/view
         [rn/image {:source (resources/get-image :starter-pack)
                    :style  {:width  76
                             :height 80}}]]
        [rn/view {:style {:padding 8}}
         [quo/text {:style {:margin-bottom 8}
                    :align :center
                    :size  :x-large}
          (i18n/label :t/starter-pack-title)]
         [quo/text {:align :center}
          (i18n/label :t/starter-pack-description)]]
        [rn/view {:style {:border-radius      8
                          :border-width       1
                          :border-color       colors/gray-lighter
                          :width              "100%"
                          :margin-vertical    8
                          :padding-vertical   8
                          :padding-horizontal 12}}
         [perk {:image :SNT-asset
                :name  "SNT"
                :value (get-in starter-pack-amount [:tokens-amount 0])}]
         [perk {:image :DAI-asset
                :name  "DAI"
                :value (get starter-pack-amount [:tokens-amount 1])}]]
        [rn/view {:style {:margin-vertical 8}}
         [quo/button {:on-press #(re-frame/dispatch [::acquisition/advertiser-decision :accept])}
          (i18n/label :t/accept)]]
        [quo/button {:type     :secondary
                     :on-press #(re-frame/dispatch [::acquisition/advertiser-decision :decline])}
         (i18n/label :t/decline)]
        [rn/view {:padding-vertical 8}
         [quo/text {:color :secondary
                    :align :center
                    :size  :small}
          (i18n/label :t/invite-privacy-policy1)
          [quo/text {:color    :link
                     :on-press #(re-frame/dispatch [::invite/terms-and-conditions])}
           (i18n/label :t/invite-privacy-policy2)]]]]])))

