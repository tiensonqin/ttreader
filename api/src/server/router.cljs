(ns server.router
  (:require ["express" :as express]
            [server.twitter :as twitter]
            [goog.object :as gobj]))

(def router (express/Router.))

(defn get-param
  [req key]
  (-> (gobj/get req "params")
      (gobj/get key)))

(.get router "/search/:q/:since_id/:max_id"
      (fn [req res]
        (let [q (get-param req "q")
              since-id (get-param req "since_id")
              max-id (get-param req "max_id")
              ]
          (twitter/search q since-id max-id
                          (fn [_ data]
                            (.send res (clj->js {:success data}) ))))))
