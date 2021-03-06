(ns status-im.ui.components.bottom-sheet.events
  (:require [status-im.utils.fx :as fx]
            [status-im.utils.handlers :as handlers]))

(fx/defn show-bottom-sheet
  [{:keys [db]} {:keys [view options]}]
  {:db (assoc db
              :bottom-sheet/show? true
              :bottom-sheet/view view
              :bottom-sheet/options options)})

(fx/defn hide-bottom-sheet
  [{:keys [db]}]
  {:db (assoc db :bottom-sheet/show? false)})

(handlers/register-handler-fx
 :bottom-sheet/hide
 (fn [cofx]
   (hide-bottom-sheet cofx)))

(handlers/register-handler-fx
 :bottom-sheet/show-sheet
 (fn [cofx [_ view options]]
   (show-bottom-sheet
    cofx
    {:view    view
     :options options})))
