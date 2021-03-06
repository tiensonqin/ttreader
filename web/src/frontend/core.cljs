(ns frontend.core
  (:require [rum.core :as rum]
            [clojure.string :as str]
            [frontend.storage :as storage]
            [cljs.tools.reader :as reader]
            [clojure.string :as string]
            ["dayjs" :as day]
            ["dayjs/plugin/relativeTime" :as day-relative]
            [goog.object :as gobj]))

(.extend day day-relative)

(defn ev [e] (aget e "target" "value"))

;; define your app data so that it doesn't get over-written on reload

(def channels (storage/get-item "channels"))
(defonce app-state
  (atom {:loading? false
         :current-channel (ffirst channels)
         :channels channels
         :tweets {}}))

(def mention-regex "(@\\w+)")

(defn to-clj
  "simplified js->clj for JSON data, :key-fn default to keyword"
  ([x] (to-clj x {}))
  ([x opts]
   (cond
     (nil? x)
     x

     (number? x)
     x

     (string? x)
     x

     (boolean? x)
     x

     (array? x)
     (into [] (map #(to-clj % opts)) (array-seq x))

     :else ;; object
     (reduce
      (fn [result key]
        (let [value
              (gobj/get x key)

              key-fn
              (get opts :key-fn keyword)]

          (assoc result (key-fn (to-clj key opts)) (to-clj value opts))
          ))
      {}
      (gobj/getKeys x)))))

(defonce dev? ^boolean goog.DEBUG)
(def prod-api-url "https://ttreader.vercel.app")
(def api
  (if dev?
    "http://localhost:3000"
    prod-api-url))

(defn fetch-tweets
  [channel-name]
  (let [config (get (:channels @app-state) channel-name)
        {:keys [users keyword]} config
        keyword (when-not (string/blank? keyword) keyword)]
    (when (or (seq users) keyword)
      (let [q (if keyword
                keyword
                (->> (map #(str "from:" %) users)
                     (str/join "+OR+")))]
        (swap! app-state assoc :loading? true)
        (-> (js/fetch
             (str api "/api/search/" q "/0/0")
             (clj->js {:method "GET"
                       :headers {"Accept" "application/json"
                                 "Content-Type" "application/json"
                                 "Origin" prod-api-url}}))
            (.then (fn [resp]
                     (if (not (nil? resp))
                       (let [ok (.-ok resp)]
                         (-> (.json resp)
                             (.then (fn [resp]
                                      (if ok
                                        (let [res (to-clj resp)]
                                          (swap! app-state (fn [s]
                                                             (-> (update s :tweets
                                                                         (fn [tweets]
                                                                           (assoc tweets channel-name
                                                                                  (get-in res [:success :statuses]))))
                                                                 (assoc :loading? false)))))
                                        (.dir js/console resp)))))))))
            (.catch (fn [err]
                      (swap! app-state assoc :loading? false)
                      (.dir js/console err))))))))

(defn with-user-mentions
  [text mentions]
  (if (seq mentions)
    (let [mentions (zipmap (map :screen_name mentions) mentions)]
      [:div
       (->> (string/split text (re-pattern mention-regex))
            (map (fn [text]
                   (or (when (= \@ (first text)) ; mention
                         (when-let [mention (get mentions (subs text 1))]
                           [:a {:href (str "https://twitter.com/" (subs text 1))}
                            text]))
                       [:span text]))))])
    text))

(rum/defc tweet < {:key-fn (fn [data]
                             (:id_str data))}
  [{:keys [full_text user created_at id_str retweet_count favorite_count entities retweeted_status]
    :as data}]
  (prn data)
  (let [text (or (:full_text retweeted_status) full_text)]
    [:div {:class "card"
           :style {:marginBottom 20}
           :onClick (fn [] (prn "clicked"))}
     [:div {:class "card-content"}
      [:div {:class "media"}
       [:div {:class "media-left"}
        [:figure {:class "image is-48x48"}
         [:img {:src (:profile_image_url user)
                :style {:borderRadius 4}}]]]
       [:div {:class "media-content"}
        [:p {:class "title is-4"} (:name user)]
        [:p {:class "subtitle is-6"} (str "@" (:screen_name user))]]

       [:span {:class "icon"}
        [:a {:href (str "https://twitter.com/" (:screen_name user) "/status/" id_str)
             :target "_blank"
             :class "fa fa-twitter"
             :style {:color "#666"}}]]]
      [:div {:class "content"}
       (with-user-mentions text (:user_mentions entities))
       [:br]
       (when-let [urls (:urls entities)]
         (for [{:keys [expanded_url]} urls]
           [:a {:key expanded_url
                :href expanded_url
                :target "_blank"}
            expanded_url]))
       (when-let [media (:media entities)]
         (when (seq media)
           (for [{:keys [type media_url]} media]
             (case type
               "photo"
               [:img {:key media_url
                      :src media_url
                      :style {:borderRadius 8}}]
               nil))))
       [:div {:style {:float "right"
                      :fontSize 12}}
        [:span {:style {:margin-right 12
                        :opacity 0.3}}
         "Fav: " favorite_count]
        [:span {:style {:margin-right 12
                        :opacity 0.3}}
         "RT: " retweet_count]
        [:span
         (.fromNow (new day created_at))]]]]]))

(rum/defcc new-channel <
  rum/reactive
  (rum/local nil ::channel-name)
  (rum/local nil ::users)
  (rum/local nil ::keyword)
  (rum/local nil ::config)
  (rum/local false ::channel-name-invalid?)
  (rum/local false ::users-or-keyword-invalid?)
  (rum/local false ::add-channel-modal?)
  (rum/local false ::edit-modal?)
  {:will-mount (fn [state]
                 (fetch-tweets (ffirst channels))
                 state)}
  [comp]
  (let [state (rum/react app-state)
        s @(rum/state comp)
        channel-name-invalid? (::channel-name-invalid? s)
        channel-name (::channel-name s)
        users (::users s)
        keyword (::keyword s)
        users-or-keyword-invalid? (::users-or-keyword-invalid? s)
        add-channel-modal? (::add-channel-modal? s)
        config (::config s)
        edit-modal? (::edit-modal? s)]
    [:div {:style {:position "fixed"
                   :top 0
                   :padding "0 20px"
                   :z-index 10
                   :background "beige"
                   :width "inherit"
                   :max-width "inherit"}}
     [:div {:class "tags"
            :style {:margin-bottom 0}}
      [:div {:style {:flex 1}}
       (if (:channels state)
         (for [[channel-name _] (:channels state)]
           [:a {:onClick (fn []
                           (swap! app-state assoc :current-channel channel-name)
                           ;; load data
                           (fetch-tweets channel-name))
                :key channel-name}
            [:span {:class (if (= channel-name (:current-channel state))
                             "tag is-primary is-medium"
                             "tag is-medium is-dark")
                    :style {:margin-right 10
                            :margin-bottom 0}}
             channel-name]])
         [:span {:style {:fontWeight "600"}}
          "TTreader"])]
      [:span {:class "icon is-medium"}
       [:a {:class "fa fa-pencil"
            :style {:color "#000"
                    :marginTop -8
                    :marginRight 10}
            :onClick (fn []
                       (reset! edit-modal? true))}]]

      [:div {:class (if @edit-modal?
                      "modal is-active"
                      "modal")}
       [:div {:class "modal-background"}]
       [:div {:class "modal-content"
              :style {:padding 80}}
        [:h1 {:class "title"
              :style {:color "#ddd"}}
         "Edit configuration"]
        [:div {:class "field"}
         [:div {:class "control"}
          (let [value (or (:channels state) "")]
            [:textarea {:class "textarea"
                        :rows 10
                        :default-value value
                        :on-change (fn [e] (reset! config (ev e)))}])
          ]]
        [:div {:class "control"}
         [:a {:class "button is-primary"
              :onClick (fn []
                         (let [channels (if-not (str/blank? @config)
                                          (reader/read-string @config)
                                          (:channels state))]
                           (storage/set-item! "channels" channels)
                           (swap! app-state assoc :channels channels)
                           ;; close modal
                           (reset! edit-modal? false)

                           (fetch-tweets (ffirst channels))))}
          "Submit"]]]
       [:a {:class "modal-close is-large"
            :aria-label "close"
            :onClick (fn []
                       (reset! edit-modal? false))}]]

      [:a {:style {:fontSize 30
                   :marginTop -12
                   :color "#000"}
           :onClick (fn []
                      (reset! add-channel-modal? true))}
       "+"]]

     [:div {:class (if @add-channel-modal?
                     "modal is-active"
                     "modal")}
      [:div {:class "modal-background"}]
      [:div {:class "modal-content"
             :style {:padding 80}}
       [:h1 {:class "title"
             :style {:color "#ddd"}}
        "Create a new channel "]
       [:div {:class "field"}
        [:label {:class "label"
                 :style {:color "#159b85"}}
         "Channel name"]
        [:div {:class "control"}
         [:input {:class "input"
                  :type "text"
                  :onChange (fn [e]
                              (reset! channel-name-invalid? false)
                              (reset! channel-name (ev e)))}]
         (if @channel-name-invalid?
           [:p {:class "help is-danger"}
            "Channel name can't be blank."])]]

       [:div {:class "field"}
        [:label {:class "label"
                 :style {:color "#159b85"}}
         "Users"]
        [:div {:class "control"}
         [:textarea {:class "textarea"
                     :placeholder "e.g. ProfFeynman, logseq \nUsernames are seperated by ,"
                     :onChange (fn [e]
                                 (reset! users (ev e)))}]]]

       [:div {:class "field"}
        [:label {:class "label"
                 :style {:color "#159b85"}}
         "Keyword"]
        [:div {:class "control"}
         [:textarea {:class "textarea"
                     :placeholder "e.g. javascript"
                     :onChange (fn [e]
                                 (reset! keyword (ev e)))}]]]

       (if @users-or-keyword-invalid?
         [:p {:class "help is-danger"}
          "Users or keyword can't be blank."])

       [:div {:class "control"}
        [:a {:class "button is-primary"
             :onClick (fn []
                        (cond
                          (str/blank? @channel-name)
                          (reset! channel-name-invalid? true)

                          (and (str/blank? @users)
                               (str/blank? @keyword))
                          (reset! users-or-keyword-invalid? true)

                          :else
                          (let [channels (assoc (:channels state)
                                                @channel-name
                                                (cond->
                                                  {}

                                                  (not (string/blank? @users))
                                                  (assoc :users (set (str/split @users #",[\s]*")))

                                                  @keyword
                                                  (assoc :keyword @keyword)))]
                            (prn {:channels channels})
                            (storage/set-item! "channels" channels)
                            (swap! app-state assoc :channels channels)
                            ;; close modal
                            (reset! add-channel-modal? false)
                            (reset! channel-name nil)
                            (reset! users nil)
                            (reset! keyword nil)
                            (fetch-tweets (ffirst channels)))))}
         "Submit"]]]
      [:a {:class "modal-close is-large"
           :aria-label "close"
           :onClick (fn []
                      (reset! add-channel-modal? false))}]]]))

(rum/defc tweets < rum/reactive
  []
  (let [{:keys [current-channel tweets channels]
         :as app-state} (rum/react app-state)]
    [:div
     (let [tweets (get tweets current-channel)]
       (cond
         (nil? channels)
         "Click `+` to add a new channel."

         :else
         (for [data tweets]
           (tweet data))))]))

(rum/defc home < rum/reactive
  []
  (let [{:keys [loading?]} (rum/react app-state)]
    [:div {:id "root-div"
           :style {:background "cadetblue"}}
     [:div {:id "root-container"
            :class "container"
            :style {:background "beige"
                    :maxWidth 600}}
      [:div {:style {:position "relative"
                     :max-width 600
                     :width "100%"}}
       (new-channel)
       (if loading?
         [:p "Loading ..."])
       [:div {:style {:margin-top 32
                      :padding 20}}
        (tweets)]]]]))

(defn start []
  (rum/mount (home)
             (.getElementById js/document "app")))

(defn ^:export init []
  ;; init is called ONCE when the page loads
  ;; this is called in the index.html and must be exported
  ;; so it is available even in :advanced release builds

  (start))

(defn stop []
  ;; stop is called before any code is reloaded
  ;; this is controlled by :before-load in the config
  (js/console.log "stop"))
