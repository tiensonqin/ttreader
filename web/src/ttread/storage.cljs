(ns ttread.storage
  (:require [cljs.tools.reader :as reader]))

(defn set-item!
  "Set `key' in browser's localStorage to `val`."
  [key val]
  (.setItem (.-localStorage js/window) key (pr-str val)))

(defn get-item
  "Returns value of `key' from browser's localStorage."
  [key]
  (-> (.getItem (.-localStorage js/window) key)
      (reader/read-string)))

(defn remove-item!
  "Remove the browser's localStorage value for the given `key`"
  [key]
  (.removeItem (.-localStorage js/window) key))
