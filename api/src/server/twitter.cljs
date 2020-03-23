(ns server.twitter
  (:require ["twitter" :as twitter]))

(def cred
  {:consumer_key js/process.env.TWITTER_CONSUMER_KEY
   :consumer_secret js/process.env.TWITTER_CONSUMER_SECRET
   :access_token_key js/process.env.TWITTER_ACCESS_TOKEN_KEY
   :access_token_secret js/process.env.TWITTER_ACCESS_TOKEN_SECRET})

(defonce client (twitter (clj->js cred)))

(defn search
  [q since-id max-id callback]
  (let [params (clj->js
                {:q q
                 :count 100
                 :since_id since-id
                 :max_id max-id
                 :tweet_mode "extended"})]
    (.get client "search/tweets" params callback)))
