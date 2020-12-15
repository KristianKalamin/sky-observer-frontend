(ns app
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [dommy.core :as dommy]
            [goog.async.Debouncer]
            [cljs-http.client :as http]
            [cljs.core.async :refer [<!]]))

(def tile-url
  "https://api.maptiler.com/maps/basic/{z}/{x}/{y}.png?key=Y7YjJp1Xw0igXnskUdzY")

(def done-typing-interval 2000)
(def l-map (-> js/L (.map "map")))
(def locations (atom (list)))
(def layer-group (-> js/L (.layerGroup)))

(let [m (-> l-map
            (.setView (array 44.7855611 20.4732303) 12))
      tile1 (-> js/L (.tileLayer tile-url
                                 #js{:attribution "<a href='https://www.maptiler.com/copyright/' target='_blank'>&copy; MapTiler</a> <a href='https://www.openstreetmap.org/copyright' target='_blank'>&copy; OpenStreetMap contributors</a>"
                                     :tileSize 512
                                     :zoomOffset -1
                                     :minZoom 1}))]

  (-> m
      (.-doubleClickZoom)
      (.disable))

  (-> tile1
      (.addTo m))
  (-> layer-group
      (.addTo m)))

(defn debounce [f interval]
  (let [dbnc (goog.async.Debouncer. f interval)]
    (fn [& args] (.apply (.-fire dbnc) dbnc (to-array args)))))

(def search-items (dommy/sel1 :#items))

(defn set-pin [lat lng]
  (-> layer-group
      (.clearLayers))

  (-> js/L
      (.marker (array lat lng))
      (.addTo layer-group))

  (-> (js/jQuery "#lat-input")
      (.val lat))

  (-> (js/jQuery "#lon-input")
      (.val lng))

  (-> l-map (.setView (array lat lng))))

(defn search-complet [result]
  (let [{lat :lat
         lon :lon
         place :place} result]

    (set-pin lat lon)

    (-> (js/jQuery "#location")
        (.val place))))

(defn show-dropdown-menu []
  (if (> (count @locations) 1)
    (do
      (-> (js/jQuery "#search-options")
          (.css "display" "block"))
      (->
       (js/jQuery "#search-options")
       (.children)
       (.empty))

      (doseq [location @locations]
        (->
         (js/jQuery "#search-options")
         (.children)
         (.append
          (str "<div class=\"item\">" (location :place) "</div>")))))

    (search-complet @locations)) ;else
  )

(add-watch locations :location-watch show-dropdown-menu)

(defn get-locations [query]
  (go (let [response (<! (http/post "http://localhost:882/locations" {:json-params {:location query}}))
            js-response (-> js/JSON
                             (.parse (:body response)))]

        (reset! locations (js->clj js-response :keywordize-keys true)))))


(defn get-location-from-coordinates [lat lon]
  (go (let [response (<! (http/post "http://localhost:882/location" {:json-params {:lat lat
                                                                                   :lon lon}}))
            js-response (-> js/JSON
                             (.parse (:body response)))]

        (search-complet (js->clj js-response :keywordize-keys true)))))

(defn set-pin-on-click [e]
  (let [{lat :lat
         lon :lng} (js->clj (-> l-map
                                (.mouseEventToLatLng e)) :keywordize-keys true)]
    (get-location-from-coordinates lat lon)))

(defn search-item-click [e]
  (let [location-name (-> e
                          (.-target)
                          (.-innerHTML))]
    (-> (js/jQuery "#location")
        (.val location-name))

    (-> (js/jQuery "#search-options")
        (.css "display" "none"))

    (let [{lat :lat
           lon :lon} (nth (filter (fn [loc]
                                    (= (get loc :place) location-name)) @locations) 0)]
      (set-pin lat lon))))


(dommy/listen! [search-items :.item] :click search-item-click)

(defn fetch-locations []
  (js/console.log "fetching locations")
  (let [input (-> (js/jQuery "#location")
                  (.val))]
    (get-locations input)))


(-> (js/jQuery "#location")
    (.keyup (debounce fetch-locations done-typing-interval)))

(-> (js/jQuery "#map")
    (.dblclick set-pin-on-click))

(defn hide-search-result [e]
  (let [container (-> (js/jQuery "#search-options"))]
    (if (and (not (-> container
                      (.is
                       (-> e (.-target)))))
             (= (-> container
                    (.has (-> e (.-target)))
                    (.-length)) 0))
      (-> container
          (.hide))

      :nothing) ;else
    ))

(-> (js/jQuery (js* "document"))
    (.mouseup hide-search-result))