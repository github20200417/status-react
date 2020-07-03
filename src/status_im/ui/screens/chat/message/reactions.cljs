(ns status-im.ui.screens.chat.message.reactions
  (:require [status-im.react-native.resources :as resources]
            [cljs-bean.core :as bean]
            [reagent.core :as reagent]
            [quo.react-native :as rn]
            [quo.react :as react]
            [quo.animated :as animated]
            [quo.components.safe-area :as safe-area]
            [quo.design-system.colors :as colors]
            [quo.core :as quo]))

(def tabbar-height 36)
(def text-input-height 54)

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

(defn picker [{:keys [outgoing]}]
  [animated/view {:style (container-style {:outgoing  outgoing})}
   [rn/view {:style (reactions-row)}
    (for [[id resource] reactions]
      ^{:key id}
      [rn/view {:padding-vertical   6
                :padding-horizontal 5}
       [rn/image {:source resource
                  :style  {:height 32
                           :width  32}}]])]
   [rn/view {:style (quick-actions-row)}
    [quo/button {:type :secondary}
     "Reply"]
    [quo/button {:type :secondary}
     "Copy"]]])

(def picker-modal
  (reagent/adapt-react-class
   (fn [props]
     (let [{message   :message
            animation :animation
            top       :top
            on-close  :onClose
            children  :children}
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
         [animated/view {:style {:top       top
                                 :transform [{:translateY (animated/mix animation 0 translate-top)}]
                                 :postion   :absolute}}
          (into [rn/view {:on-layout on-message-layout}]
                (react/get-children children))
          [animated/view {:on-layout on-picker-layout
                          :style     {:top             message-height
                                      :left            0
                                      :right           0
                                      :flex-direction  :row
                                      :justify-content (if (:outgoing message)
                                                         :flex-end
                                                         :flex-start)
                                      :flex            1
                                      :padding-top     4
                                      :padding-left    52
                                      :padding-right   8
                                      :position        :absolute
                                      :opacity         animation
                                      :transform       [{:translateX (animated/mix animation (if (:outgoing message)
                                                                                               translate-x
                                                                                               (* -1 translate-x)) 0)}
                                                        {:translateY (animated/mix animation translate-y 0)}
                                                        {:scale (animated/mix animation scale 1)}]}}
           [picker message animation]]]])))))

(defn with-reaction-picker []
  (let [ref            (react/create-ref)
        animated-state (animated/value 0)
        animation      (animated/with-timing-transition
                         animated-state
                         {:easing (:ease-out animated/easings)})
        visible        (reagent/atom false)
        on-close       (fn []
                         (animated/set-value animated-state 0)
                         (reset! visible false))
        position       (reagent/atom {})
        on-open        (fn [pos]
                         (reset! position pos)
                         (reset! visible true))]
    (fn [{:keys [message]} & children]
      [:<>
       (into [rn/touchable-highlight {:ref      ref
                                      :on-press #(get-picker-position ref on-open)}]
             children)
       [rn/modal {:visible          @visible
                  :on-request-close on-close
                  :on-show          (fn []
                                      (animated/set-value animated-state 1))
                  :transparent      true}
        (into [picker-modal {:message   message
                             :animation animation
                             :top       (:top @position)
                             :on-close  on-close}]
              children)]])))
