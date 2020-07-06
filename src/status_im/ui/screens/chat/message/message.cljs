(ns status-im.ui.screens.chat.message.message
  (:require [re-frame.core :as re-frame]
            [status-im.constants :as constants]
            [status-im.i18n :as i18n]
            [status-im.ui.components.colors :as colors]
            [status-im.ui.components.icons.vector-icons :as vector-icons]
            [status-im.ui.components.react :as react]
            [status-im.ui.screens.chat.message.command :as message.command]
            [status-im.ui.screens.chat.photos :as photos]
            [status-im.ui.screens.chat.sheets :as sheets]
            [status-im.ui.screens.chat.styles.message.message :as style]
            [status-im.ui.screens.chat.utils :as chat.utils]
            [status-im.utils.contenthash :as contenthash]
            [status-im.utils.security :as security]
            [status-im.ui.screens.chat.message.reactions :as reactions]
            [reagent.core :as reagent])
  (:require-macros [status-im.utils.views :refer [defview letsubs]]))

(defview mention-element [from]
  (letsubs [contact-name [:contacts/contact-name-by-identity from]]
    contact-name))

(defn message-timestamp
  ([message]
   [message-timestamp message false])
  ([{:keys [timestamp-str outgoing content]} justify-timestamp?]
   [react/text {:style (style/message-timestamp-text
                        justify-timestamp?
                        outgoing
                        (:rtl? content))} timestamp-str]))

(defn message-bubble-wrapper
  [message message-content appender]
  [react/view
   (style/message-view message)
   message-content
   appender])

(defview quoted-message
  [_ {:keys [from text image]} outgoing current-public-key public?]
  (letsubs [contact-name [:contacts/contact-name-by-identity from]]
    [react/view {:style (style/quoted-message-container outgoing)}
     [react/view {:style style/quoted-message-author-container}
      [chat.utils/format-reply-author
       from
       contact-name
       current-public-key
       (partial style/quoted-message-author outgoing)]]
     (if (and image
              ;; Disabling images for public-chats
              (not public?))
       [react/image {:style  {:width            56
                              :height           56
                              :background-color :black
                              :border-radius    4}
                     :source {:uri image}}]
       [react/text {:style           (style/quoted-message-text outgoing)
                    :number-of-lines 5}
        text])]))

(defn render-inline [message-text outgoing content-type acc {:keys [type literal destination]}]
  (case type
    ""
    (conj acc literal)

    "code"
    (conj acc [react/text-class (style/inline-code-style) literal])

    "emph"
    (conj acc [react/text-class (style/emph-style outgoing) literal])

    "strong"
    (conj acc [react/text-class (style/strong-style outgoing) literal])

    "link"
    (conj acc
          [react/text-class
           {:style
            {:color (if outgoing colors/white-persist colors/blue)
             :text-decoration-line :underline}
            :on-press
            #(when (and (security/safe-link? destination)
                        (security/safe-link-text? message-text))
               (re-frame/dispatch
                [:browser.ui/message-link-pressed destination]))}
           destination])

    "mention"
    (conj acc [react/text-class
               {:style {:color (cond
                                 (= content-type constants/content-type-system-text) colors/black
                                 outgoing colors/mention-outgoing
                                 :else colors/mention-incoming)}
                :on-press (when-not (= content-type constants/content-type-system-text)
                            #(re-frame/dispatch [:chat.ui/start-chat literal {:navigation-reset? true}]))}
               [mention-element literal]])
    "status-tag"
    (conj acc [react/text-class
               {:style {:color (if outgoing colors/white-persist colors/blue)
                        :text-decoration-line :underline}
                :on-press
                #(re-frame/dispatch
                  [:chat.ui/start-public-chat literal {:navigation-reset? true}])}
               "#"
               literal])

    (conj acc literal)))

(defview message-content-status [{:keys [content content-type]}]
  [react/view style/status-container
   [react/text {:style (style/status-text)}
    (reduce
     (fn [acc e] (render-inline (:text content) false content-type acc e))
     [react/text-class {:style (style/status-text)}]
     (-> content :parsed-text peek :children))]])

(defn render-block [{:keys [content outgoing content-type]} acc
                    {:keys [type ^js literal children]}]
  (case type

    "paragraph"
    (conj acc (reduce
               (fn [acc e] (render-inline (:text content) outgoing content-type acc e))
               [react/text-class (style/text-style outgoing content-type)]
               children))

    "blockquote"
    (conj acc [react/view (style/blockquote-style outgoing)
               [react/text-class (style/blockquote-text-style outgoing)
                (.substring literal 0 (dec (.-length literal)))]])

    "codeblock"
    (conj acc [react/view style/codeblock-style
               [react/text-class style/codeblock-text-style
                (.substring literal 0 (dec (.-length literal)))]])

    acc))

(defn render-parsed-text [message tree]
  (reduce (fn [acc e] (render-block message acc e)) [react/view {}] tree))

(defn render-parsed-text-with-timestamp [{:keys [timestamp-str outgoing] :as message} tree]
  (let [elements (render-parsed-text message tree)
        timestamp [react/text {:style (style/message-timestamp-placeholder outgoing)}
                   (str "  " timestamp-str)]
        last-element (peek elements)]
    ;; Using `nth` here as slightly faster than `first`, roughly 30%
    ;; It's worth considering pure js structures for this code path as
    ;; it's perfomance critical
    (if (= react/text-class (nth last-element 0))
      ;; Append timestamp to last text
      (conj (pop elements) (conj last-element timestamp))
      ;; Append timestamp to new block
      (conj elements timestamp))))

(defn text-message
  [{:keys [content outgoing current-public-key public?] :as message}]
  [message-bubble-wrapper message
   (let [response-to (:response-to content)]
     [react/view
      (when (and (seq response-to) (:quoted-message message))
        [quoted-message response-to (:quoted-message message) outgoing current-public-key public?])
      [render-parsed-text-with-timestamp message (:parsed-text content)]])
   [message-timestamp message true]])

(defn unknown-content-type
  [{:keys [outgoing content-type content] :as message}]
  [message-bubble-wrapper message
   [react/text
    {:style {:color (if outgoing colors/white-persist colors/black)}}
    (if (seq (:text content))
      (:text content)
      (str "Unhandled content-type " content-type))]])

(defn emoji-message
  [{:keys [content current-public-key outgoing public?] :as message}]
  (let [response-to (:response-to content)]
    [message-bubble-wrapper message
     [react/view {:style (style/style-message-text outgoing)}
      (when (and (seq response-to) (:quoted-message message))
        [quoted-message response-to (:quoted-message message) outgoing current-public-key public?])
      [react/text {:style (style/emoji-message message)}
       (:text content)]]
     [message-timestamp message]]))

(defn message-not-sent-text
  [chat-id message-id]
  [react/touchable-highlight
   {:on-press
    (fn []
      (re-frame/dispatch
       [:bottom-sheet/show-sheet
        {:content        (sheets/options chat-id message-id)
         :content-height 200}])
      (react/dismiss-keyboard!))}
   [react/view style/not-sent-view
    [react/text {:style style/not-sent-text}
     (i18n/label :t/status-not-sent-tap)]
    [react/view style/not-sent-icon
     [vector-icons/icon :main-icons/warning {:color colors/red}]]]])

(defn message-delivery-status
  [{:keys [chat-id message-id outgoing-status message-type]}]
  (when (and (not= constants/message-type-private-group-system-message message-type)
             (= outgoing-status :not-sent))
    [message-not-sent-text chat-id message-id]))

(defview message-author-name [from alias modal]
  (letsubs [contact-name [:contacts/raw-contact-name-by-identity from]]
    (chat.utils/format-author (or contact-name alias) modal)))

(defn message-content-wrapper
  "Author, userpic and delivery wrapper"
  [{:keys [alias first-in-group? display-photo? identicon display-username?
           from outgoing]
    :as   message} content modal]
  [react/view {:style               (style/message-wrapper message)
               :pointer-events      :box-none
               :accessibility-label :chat-item}
   [react/view {:style          (style/message-body message)
                :pointer-events :box-none}
    (when display-photo?
      [react/view (style/message-author-userpic outgoing)
       (when (or (and (not outgoing) modal) first-in-group?)
         [react/touchable-highlight {:on-press #(re-frame/dispatch [:chat.ui/show-profile from])}
          [photos/member-identicon identicon]])])
    [react/view (style/message-author-wrapper outgoing display-photo?)
     (when (or (and (not outgoing) modal) display-username?)
       [react/touchable-opacity {:style    style/message-author-touchable
                                 :on-press #(re-frame/dispatch [:chat.ui/show-profile from])}
        [message-author-name from alias modal]])
     ;;MESSAGE CONTENT
     [react/view
      content]]]
   ; delivery status
   [react/view (style/delivery-status outgoing)
    [message-delivery-status message]]])

(defn message-content-image [{:keys [content outgoing]}]
  (let [dimensions (reagent/atom [260 260])
        uri (:image content)]
    (react/image-get-size
     uri
     (fn [width height]
       (let [k (/ (max width height) 260)]
         (reset! dimensions [(/ width k) (/ height k)]))))
    (fn []
      [react/view {:style (style/image-content outgoing)}
       [react/image {:style {:width (first @dimensions) :height (last @dimensions)}
                     :resize-mode :contain
                     :source {:uri uri}}]])))

(defn sticker-message-press-handlers [{:keys [content] :as message}]
  (let [pack (get-in content [:sticker :pack])]
    {:on-press      (fn [_]
                      (when pack
                        (re-frame/dispatch [:stickers/open-sticker-pack pack]))
                      (re-frame/dispatch [:chat.ui/set-chat-ui-props {:input-bottom-sheet nil}])
                      (react/dismiss-keyboard!))
     :on-long-press #(re-frame/dispatch [:bottom-sheet/show-sheet
                                         {:content (sheets/sticker-long-press message)
                                          :height  64}])}))

(defmulti ->message :content-type)

(defmethod ->message constants/content-type-command
  [message]
  [message.command/command-content message-content-wrapper message])

(defmethod ->message constants/content-type-system-text [{:keys [content] :as message}]
  [react/view {:accessibility-label :chat-item}
   [react/view (style/system-message-body message)
    [message-bubble-wrapper message
     [react/view
      [render-parsed-text message (:parsed-text content)]]]]])

(defmethod ->message constants/content-type-text [message {:keys [on-long-press modal]}]
  [message-content-wrapper message
   [react/touchable-highlight {:on-press      (fn [_]
                                                (re-frame/dispatch [:chat.ui/set-chat-ui-props {:input-bottom-sheet nil}])
                                                (react/dismiss-keyboard!))
                               :on-long-press on-long-press}
    [text-message message]]
   modal])

(defmethod ->message constants/content-type-status [message]
  [message-content-wrapper message
   [message-content-status message]])

(defmethod ->message constants/content-type-emoji [message {:keys [on-long-press modal]}]
  [message-content-wrapper message
   [react/touchable-highlight {:on-press      (fn [_]
                                                (re-frame/dispatch [:chat.ui/set-chat-ui-props {:input-bottom-sheet nil}])
                                                (react/dismiss-keyboard!))
                               :on-long-press on-long-press}
    [emoji-message message]]
   modal])

(defmethod ->message constants/content-type-sticker [{:keys [content] :as message}]
  [message-content-wrapper message
   [react/touchable-highlight (sticker-message-press-handlers message)
    [react/image {:style  {:margin 10 :width 140 :height 140}
                  ;;TODO (perf) move to event
                  :source {:uri (contenthash/url (-> content :sticker :hash))}}]]])

(defmethod ->message constants/content-type-image [{:keys [content] :as message}]
  [message-content-wrapper message
   [react/touchable-highlight {:on-press      (fn [_]
                                                (when (:image content)
                                                  (re-frame/dispatch [:navigate-to :image-preview message]))
                                                (re-frame/dispatch [:chat.ui/set-chat-ui-props {:input-bottom-sheet nil}])
                                                (react/dismiss-keyboard!))
                               :on-long-press #(re-frame/dispatch [:bottom-sheet/show-sheet
                                                                   {:content (sheets/image-long-press message false)
                                                                    :height  160}])}
    [message-content-image message]]])

(defmethod ->message :default [message]
  [message-content-wrapper message
   [unknown-content-type message]])

(defn chat-message [message]
  [reactions/with-reaction-picker {:message    message
                                   :on-reply   #(re-frame/dispatch [:chat.ui/reply-to-message message])
                                   :on-copy    #(react/copy-to-clipboard (get-in message [:content :text]))
                                   :send-emoji println
                                   :render     ->message}])
