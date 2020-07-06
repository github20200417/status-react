(ns status-im.ui.screens.chat.message.reactions
  (:require [status-im.react-native.resources :as resources]
            [cljs-bean.core :as bean]
            [status-im.i18n :as i18n]
            [reagent.core :as reagent]
            [quo.react-native :as rn]
            [quo.react :as react]
            [quo.animated :as animated]
            [quo.components.safe-area :as safe-area]
            [quo.design-system.colors :as colors]
            [quo.core :as quo]))

(def tabbar-height 36)
(def text-input-height 54)

(def animation-duration 200)

(def scale         0.8)
(def translate-x   27)
(def translate-y   -54)

(def reactions [[:love (:love resources/reactions)]
                [:thumbs-up (:thumbs-up resources/reactions)]
                [:thumbs-down (:thumbs-down resources/reactions)]
                [:laugh (:laugh resources/reactions)]
                [:sad (:sad resources/reactions)]
                [:angry (:angry resources/reactions)]])

(defn measure-in-window [ref cb]
  (.measureInWindow ^js ref cb))

(defn get-picker-position [^js ref cb]
  (some-> ref
          react/current-ref
          (measure-in-window
           (fn [x y]
             (cb {:top  y
                  :left x})))))

(def picker-wrapper-style
  {:position       :absolute
   :left           0
   :right          0
   :flex-direction :row
   :flex           1
   :padding-top    4
   :padding-left   52
   :padding-right  8})

(defn container-style [{:keys [outgoing]}]
  (merge {:border-top-left-radius     16
          :border-top-right-radius    16
          :border-bottom-right-radius 16
          :border-bottom-left-radius  16
          :background-color           :white}
         (if outgoing
           {:border-top-right-radius 4}
           {:border-top-left-radius 4})))

(defn reactions-row []
  {:flex-direction     :row
   :pading-vertical    6
   :padding-horizontal 7})

(defn quick-actions-row []
  {:flex-direction   :row
   :justify-content  :space-evenly
   :border-top-width 1
   :border-top-color (:ui-01 @colors/theme)})

(defn picker [{:keys [outgoing on-copy on-reply send-emoji]}]
  [animated/view {:style (container-style {:outgoing outgoing})}
   [rn/view {:style (reactions-row)}
    (for [[id resource] reactions]
      ^{:key id}
      [rn/touchable-opacity {:on-press #(send-emoji id)
                             :style    {:padding-vertical   6
                                        :padding-horizontal 5}}
       [rn/image {:source resource
                  :style  {:height 32
                           :width  32}}]])]
   [rn/view {:style (quick-actions-row)}
    [quo/button {:type     :secondary
                 :on-press on-reply}
     (i18n/label :t/message-reply)]
    [quo/button {:type     :secondary
                 :on-press on-copy}
     (i18n/label :t/sharing-copy-to-clipboard)]]])

(def picker-modal
  (reagent/adapt-react-class
   (fn [props]
     (let [{outgoing   :outgoing
            animation  :animation
            top        :top
            on-close   :onClose
            on-reply   :onReply
            on-copy    :onCopy
            send-emoji :sendEmoji
            children   :children}
           (bean/bean props)
           {bottom-inset :bottom}  (safe-area/use-safe-area)
           {window-height :height} (rn/use-window-dimensions)

           {message-height    :height
            on-message-layout :on-layout} (rn/use-layout)

           {picker-height    :height
            on-picker-layout :on-layout} (rn/use-layout)

           full-height   (+ message-height picker-height top)
           max-height    (- window-height bottom-inset tabbar-height text-input-height)
           translate-top (- (max 0 (- full-height max-height)))]
       (reagent/as-element
        [:<>
         [rn/view {:style {:position :absolute
                           :flex     1
                           :top      0
                           :bottom   0
                           :left     0
                           :right    0}}
          [rn/touchable-without-feedback
           {:on-press on-close}
           [animated/view {:style {:flex             1
                                   :opacity          animation
                                   :background-color "rgba(0,0,0,0.5)"}}]]]
         [animated/view {:pointer-events :box-none
                         :style          {:top       top
                                          :opacity   animation
                                          :transform [{:translateY (animated/mix animation 0 translate-top)}]
                                          :postion   :absolute}}
          (into [rn/view {:pointer-events :box-none
                          :on-layout      on-message-layout}]
                (react/get-children children))
          [animated/view {:on-layout      on-picker-layout
                          :pointer-events :box-none
                          :style          (merge picker-wrapper-style
                                                 {:justify-content (if outgoing
                                                                     :flex-end
                                                                     :flex-start)
                                                  :top             message-height
                                                  :opacity         animation
                                                  :transform       [{:translateX (animated/mix animation (if outgoing
                                                                                                           translate-x
                                                                                                           (* -1 translate-x)) 0)}
                                                                    {:translateY (animated/mix animation translate-y 0)}
                                                                    {:scale (animated/mix animation scale 1)}]})}
           [picker {:outgoing   outgoing
                    :on-reply   on-reply
                    :on-copy    on-copy
                    :send-emoji send-emoji
                    :animation  animation}]]]])))))

(defn with-reaction-picker []
  (let [ref            (react/create-ref)
        animated-state (animated/value 0)
        animation      (animated/with-timing-transition
                         animated-state
                         {:duration animation-duration
                          :easing   (:ease-out animated/easings)})
        visible        (reagent/atom false)
        on-close       (fn []
                         (animated/set-value animated-state 0)
                         (js/setTimeout
                          #(reset! visible false)
                          animation-duration))
        position       (reagent/atom {})
        on-open        (fn [pos]
                         (reset! position pos)
                         (reset! visible true))]
    (fn [{:keys [message render on-reply on-copy send-emoji]}]
      [:<>
       [rn/view {:ref ref}
        [animated/view {:style {:opacity (animated/mix animation 1 0)}}
         [render message {:modal         false
                          :on-long-press (fn []
                                           (get-picker-position ref on-open))}]]]
       [rn/modal {:visible          @visible
                  :on-request-close on-close
                  :on-show          (fn []
                                      (js/requestAnimationFrame
                                       #(animated/set-value animated-state 1)))
                  :transparent      true}
        [picker-modal {:outgoing   (:outgoing message)
                       :animation  animation
                       :top        (:top @position)
                       :on-close   on-close
                       :on-reply   (fn []
                                     (on-close)
                                     (js/setTimeout on-reply animation-duration))
                       :on-copy    (fn []
                                     (on-close)
                                     (js/setTimeout on-copy animation-duration))
                       :send-emoji (fn [emoji]
                                     (on-close)
                                     (js/setTimeout #(send-emoji emoji) animation-duration))}
         [render message {:modal true}]]]])))
