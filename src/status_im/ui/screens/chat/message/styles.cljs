(ns status-im.ui.screens.chat.message.styles
  (:require [quo.design-system.colors :as colors]
            [status-im.ui.screens.chat.styles.photos :as photos]))

(defn picker-wrapper-style [{:keys [display-photo? outgoing]}]
  (merge {:position       :absolute
          :left           0
          :right          0
          :flex-direction :row
          :flex           1
          :padding-top    4
          :padding-right  8}
         (if outgoing
           {:justify-content :flex-end}
           {:justify-content :flex-start})
         (if display-photo?
           {:padding-left (+ 16 photos/default-size)}
           {:padding-left 8})))

(defn container-style [{:keys [outgoing]}]
  (merge {:border-top-left-radius     16
          :border-top-right-radius    16
          :border-bottom-right-radius 16
          :border-bottom-left-radius  16
          :background-color           :white}
         (if outgoing
           {:border-top-right-radius 4}
           {:border-top-left-radius 4})))

(defn reactions-picker-row []
  {:flex-direction     :row
   :pading-vertical    6
   :padding-horizontal 7})

(defn quick-actions-row []
  {:flex-direction   :row
   :justify-content  :space-evenly
   :border-top-width 1
   :border-top-color (:ui-01 @colors/theme)})

(defn reaction-style [{:keys [outgoing own]}]
  (merge {:border-top-left-radius     10
          :border-top-right-radius    10
          :border-bottom-right-radius 10
          :border-bottom-left-radius  10
          :flex-direction             :row
          :margin-vertical            2
          :padding-right              8
          :padding-left               2
          :padding-vertical           2}
         (if own
           {:background-color (:interactive-01 @colors/theme)}
           {:background-color (:interactive-02 @colors/theme)})
         (if outgoing
           {:border-top-right-radius 2
            :margin-left             4}
           {:border-top-left-radius 2
            :margin-right           4})))

(defn reaction-quantity-style [{:keys [own]}]
  {:font-size   12
   :line-height 16
   :color       (if own
                  (:text-05 @colors/theme)
                  (:text-01 @colors/theme))})

(defn reactions-row [{:keys [outgoing]}]
  {:flex-direction  :row
   :justify-content (if outgoing :flex-end :flex-start)
   :padding-right   8
   :padding-left    (+ 16 photos/default-size)})
