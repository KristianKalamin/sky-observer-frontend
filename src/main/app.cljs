(ns app
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [dommy.core :as dommy]
            [goog.async.Debouncer]
            [cljs-http.client :as http]
            [cljs.core.async :refer [<!]]))

(def tile-url
  "https://api.maptiler.com/maps/basic/{z}/{x}/{y}.png?key=Y7YjJp1Xw0igXnskUdzY")

(def done-typing-interval 2000)
(def l-map (.map js/L "map"))
(def locations (atom (list)))
(def layer-group (.layerGroup js/L))

(let [m (.setView l-map (array 44.7855611 20.4732303) 12)]
  (.addTo (.tileLayer js/L tile-url
                      #js{:attribution "<a href='https://www.maptiler.com/copyright/' target='_blank'>&copy; MapTiler</a> <a href='https://www.openstreetmap.org/copyright' target='_blank'>&copy; OpenStreetMap contributors</a>"
                          :tileSize 512
                          :zoomOffset -1
                          :minZoom 1}) m)

  (.disable (.-doubleClickZoom m))
  (.addTo layer-group m))

(defn debounce [f interval]
  (let [dbnc (goog.async.Debouncer. f interval)]
    (fn [& args] (.apply (.-fire dbnc) dbnc (to-array args)))))

(def search-items (dommy/sel1 :#items))

(defn set-pin [lat lng]
  (.clearLayers layer-group)
  (.addTo (.marker js/L (array lat lng)) layer-group)
  (.val (js/jQuery "#lat-input") lat)
  (.val (js/jQuery "#lon-input") lng)
  (.setView l-map (array lat lng)))

(defn search-complet [result]
  (let [{lat :lat
         lon :lon
         place :place} result]

    (set-pin lat lon)
    (.val (js/jQuery "#location") place)))

(defn show-dropdown-menu []
  (if (> (count @locations) 1)
    (do
      (.css (js/jQuery "#search-options") "display" "block")
      (.empty (.children (js/jQuery "#search-options")))

      (doseq [location @locations]
        (.append
         (.children (js/jQuery "#search-options"))
         (str "<div class=\"item\">" (location :place) "</div>"))))

    (search-complet @locations))) ;else


(add-watch locations :location-watch show-dropdown-menu)

(defn get-locations [query]
  (go (let [response (<! (http/post "http://localhost:882/locations" {:json-params {:location query}}))]
        (reset! locations (js->clj (.parse js/JSON (:body response)) :keywordize-keys true)))))


(defn get-location-from-coordinates [lat lon]
  (go (let [response (<! (http/post "http://localhost:882/location" {:json-params {:lat lat
                                                                                   :lon lon}}))]
        (search-complet (js->clj (.parse js/JSON (:body response)) :keywordize-keys true)))))

(defn set-pin-on-click [e]
  (let [{lat :lat
         lon :lng} (js->clj (.mouseEventToLatLng l-map e) :keywordize-keys true)]
    (get-location-from-coordinates lat lon)))

(defn search-item-click [e]
  (let [location-name (.-innerHTML (.-target e))]
    (.val (js/jQuery "#location") location-name)
    (.css (js/jQuery "#search-options") "display" "none")

    (let [{lat :lat
           lon :lon} (nth (filter (fn [loc]
                                    (= (get loc :place) location-name)) @locations) 0)]
      (set-pin lat lon))))


(dommy/listen! [search-items :.item] :click search-item-click)

(defn fetch-locations []
  (get-locations (.val (js/jQuery "#location"))))

(.keyup (js/jQuery "#location") (debounce fetch-locations done-typing-interval))

(.dblclick (js/jQuery "#map") set-pin-on-click)

(defn hide-search-result [e]
  (let [container (js/jQuery "#search-options")]
    (if (and (not (.is container (.-target e)))
             (= (.-length (.has container (.-target e))) 0))

      (.hide container)

      :nothing))) ;else

(.mouseup (js/jQuery (js* "document")) hide-search-result)