(ns ttread.core
  (:require [rum.core :as rum]
            [clojure.string :as str]
            [clojure.pprint :as pprint]
            [ttread.storage :as storage]
            [cljs.tools.reader :as reader]
            [cljsjs.moment]
            [goog.object :as gobj]))

(enable-console-print!)

(defn ev [e] (aget e "target" "value"))

;; define your app data so that it doesn't get over-written on reload

;; {:name "spurs" :users #{}}
(def channels (storage/get-item "channels"))
(defonce app-state
  (atom {:current-channel (ffirst channels)
         :channels channels
         :tweets {}}))

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
      (gobj/getKeys x)
      ))))

(defn fetch-tweets
  [channel-name]
  (let [users (get (:channels @app-state) channel-name)]
    (when (seq users)
      (let [q (->> (map #(str "from:" %) users)
                   (str/join "+OR+"))]
        (-> (js/fetch
             (str "https://ttreader-api.now.sh/search/" q "/0/0")
             (clj->js {:method "GET"
                       :headers {"Accept" "application/json"
                                 "Content-Type" "application/json"}}))
            (.then (fn [resp]
                     (if (not (nil? resp))
                       (let [ok (.-ok resp)]
                         (-> (.json resp)
                             (.then (fn [resp]
                                      (if ok
                                        (let [res (to-clj resp)]
                                          (swap! app-state (fn [s]
                                                             (update s :tweets
                                                                     (fn [tweets]
                                                                       (assoc tweets channel-name
                                                                              (get-in res [:success :statuses])))))))
                                        (.dir js/console resp)))))))))
            (.catch (fn [err]
                      (.dir js/console err))))))


    ))



(rum/defc tweet < {:key-fn (fn [data]
                             (:id_str data))}
  [{:keys [text user created_at id_str retweet_count favorite_count]
    :as data}]
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
     text
     [:br]
     [:span {:style {:float "right"
                     :fontSize 12}}
      (.fromNow (new js/moment created_at))]]]
   ])

(rum/defcc new-channel <
  (rum/local nil ::channel-name)
  (rum/local nil ::users)
  (rum/local nil ::config)
  (rum/local false ::channel-name-invalid?)
  (rum/local false ::users-invalid?)
  (rum/local false ::add-channel-modal?)
  (rum/local false ::edit-modal?)
  [comp state]
  (let [s @(rum/state comp)
        channel-name-invalid? (::channel-name-invalid? s)
        channel-name (::channel-name s)
        users (::users s)
        users-invalid? (::users-invalid? s)
        add-channel-modal? (::add-channel-modal? s)

        config (::config s)
        edit-modal? (::edit-modal? s)]
    [:div
     [:div {:class "tags"}
      [:div {:style {:flex 1}}
       (if (:channels state)
         (for [[channel-name users] (:channels state)]
           [:a {:onClick (fn []
                           (swap! app-state assoc :current-channel channel-name)
                           ;; load data
                           (fetch-tweets channel-name))
                :key channel-name}
            [:span {:class (if (= channel-name (:current-channel state))
                             "tag is-primary is-medium"
                             "tag is-medium is-dark")
                    :style {:margin-right 10}}
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
          [:textarea {:class "textarea"
                      :rows 10
                      :value (if @config
                               @config
                               ;; (with-out-str (pprint/pprint (:channels state)))
                               (:channels state))
                      :onChange (fn [e] (reset! config (ev e)))}]]]
        [:div {:class "control"}
         [:a {:class "button is-primary"
              :onClick (fn []
                         (let [channels (reader/read-string @config)]
                           (storage/set-item! "channels" channels)
                           (swap! app-state assoc :channels channels)
                           ;; close modal
                           (reset! edit-modal? false)))}
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
                     :placeholder "e.g. manuginobili, timduncan \nUsernames are seperated by ,"
                     :onChange (fn [e]
                                 (reset! users-invalid? false)
                                 (reset! users (ev e)))}]
         (if @users-invalid?
           [:p {:class "help is-danger"}
            "Users can't be blank."])]]


       [:div {:class "control"}
        [:a {:class "button is-primary"
             :onClick (fn []
                        (cond
                          (str/blank? @channel-name)
                          (reset! channel-name-invalid? true)

                          (str/blank? @users)
                          (reset! users-invalid? true)

                          :else
                          (let [channels (assoc (:channels state) @channel-name (set (str/split @users #",[\s]*")))]
                            (storage/set-item! "channels" channels)
                            (swap! app-state assoc :channels channels)
                            ;; close modal
                            (reset! add-channel-modal? false)
                            (reset! channel-name nil)
                            (reset! users nil))))}
         "Submit"]]]
      [:a {:class "modal-close is-large"
           :aria-label "close"
           :onClick (fn []
                      (prn "hi")
                      (reset! add-channel-modal? false))}]]]))

(rum/defc home < rum/reactive
  {:will-mount (fn [state]
                (fetch-tweets (ffirst channels))
                state)}
  []

  (let [{:keys [current-channel tweets channels]
         :as app-state} (rum/react app-state)]

    [:div {:id "root-div"
           :style {:background "cadetblue"}}
     [:div {:id "root-container"
            :class "container"
            :style {:padding 20
                    :background "beige"
                    :maxWidth 600}}

      (new-channel app-state)

      [:div
       (let [tweets (get tweets current-channel)]
         (cond
           (nil? channels)
           "Click `+` to add a new channel."

           :else
           (for [data tweets]
             (tweet data))))]]]))

(rum/mount (home)
           (.getElementById js/document "app"))

(defn on-js-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
  )
