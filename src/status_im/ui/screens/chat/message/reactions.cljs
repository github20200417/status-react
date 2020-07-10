(ns status-im.ui.screens.chat.message.reactions
  (:require [re-frame.core :as re-frame]
            [status-im.ui.screens.chat.message.reactions-picker :as reaction-picker]
            [status-im.ui.screens.chat.message.reactions-row :as reaction-row]
            [reagent.core :as reagent]
            [quo.react-native :as rn]
            [quo.react :as react]
            [quo.animated :as animated]))

(defn measure-in-window [ref cb]
  (.measureInWindow ^js ref cb))

(defn get-picker-position [^js ref cb]
  (some-> ref
          react/current-ref
          (measure-in-window
           (fn [x y width height]
             (cb {:top    y
                  :left   x
                  :width  width
                  :height height})))))

(defn with-reaction-picker []
  (let [ref              (react/create-ref)
        animated-state   (animated/value 0)
        spring-animation (animated/with-spring-transition
                           animated-state
                           (:jump animated/springs))
        animation        (animated/with-timing-transition
                           animated-state
                           {:duration reaction-picker/animation-duration
                            :easing   (:ease-in-out animated/easings)})
        visible          (reagent/atom false)
        actions          (reagent/atom nil)
        on-close         (fn []
                           (animated/set-value animated-state 0)
                           (js/setTimeout
                            (fn []
                              (reset! actions nil)
                              (reset! visible false))
                            reaction-picker/animation-duration))
        position         (reagent/atom {})
        on-open          (fn [pos]
                           (reset! position pos)
                           (reset! visible true))]
    (fn [{:keys [message render send-emoji]}]
      (let [reactions @(re-frame/subscribe [:chats/message-reactions (:message-id message)])]
        [:<>
         [animated/view {:style {:opacity (animated/mix animation 1 0)}}
          [rn/view {:ref         ref
                    :collapsable false}
           [render message {:modal         false
                            :on-long-press (fn [act]
                                             (reset! actions act)
                                             (get-picker-position ref on-open))}]]
          [reaction-row/message-reactions message reactions]]
         [rn/modal {:visible          @visible
                    :on-request-close on-close
                    :on-show          (fn []
                                        (js/requestAnimationFrame
                                         #(animated/set-value animated-state 1)))
                    :transparent      true}
          [reaction-picker/modal {:outgoing       (:outgoing message)
                                  :display-photo  (:display-photo? message)
                                  :animation      animation
                                  :spring         spring-animation
                                  :top            (:top @position)
                                  :left           (:left @position)
                                  :message-height (:height @position)
                                  :on-close       on-close
                                  :actions        @actions
                                  :send-emoji     (fn [emoji]
                                                    (on-close)
                                                    (js/setTimeout #(send-emoji emoji)
                                                                   reaction-picker/animation-duration))}
           [render message {:modal       true
                            :close-modal on-close}]]]]))))
