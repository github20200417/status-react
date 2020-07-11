(ns status-im.chat.models.reactions
  (:require [status-im.constants :as constants]))

(defn process-reactions
  [{:keys [db]} {:keys [chat-id emojiReaction emojiReactionRetraction content-type message-id]
                 :as   message}]
  (cond
    (= content-type constants/content-type-emoji-reaction)
    (let [{:keys [type message_id]} emojiReaction]
      {:db (assoc-in db [:reactions chat-id message_id type message-id] message)})

    (= content-type constants/content-type-emoji-reaction-retraction)
    (let [{:keys [emoji_reaction_id]} emojiReactionRetraction]
      {:db (assoc-in db [:reactions-retractions chat-id message-id emoji_reaction_id] message)})))

(defn reaction-message? [{:keys [content-type]}]
  (println content-type)
  (#{constants/content-type-emoji-reaction
     constants/content-type-emoji-reaction-retraction}
   content-type))
