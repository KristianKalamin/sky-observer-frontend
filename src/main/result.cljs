(ns result
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs-http.client :as http]
            [cemerick.url :refer (url)]
            [clojure.string :as str]
            [cljs.core.async :refer [<!]]))

(def tile-url
  "https://api.maptiler.com/maps/basic/{z}/{x}/{y}.png?key=Y7YjJp1Xw0igXnskUdzY")

(def l-map  (.map js/L "proxy-map"))

(defn load-map [^js l-map data lat lon]
  (.addTo (.tileLayer js/L tile-url
                      #js{:attribution "<a href='https://www.maptiler.com/copyright/' target='_blank'>&copy; MapTiler</a> <a href='https://www.openstreetmap.org/copyright' target='_blank'>&copy; OpenStreetMap contributors</a>"
                          :tileSize 512
                          :zoomOffset -1
                          :minZoom 1})
          (.setView l-map (array lat lon) 10))

  ; loop and set markers
  (doseq [mark data]
    (let [{lat :lat
           lon :lon} mark]
      (.addTo (.marker js/L (array lat lon)) l-map))))

(defn set-heading [location]
  (.text (js/jQuery "#heading") (str "All flybys over " location)))

(defn get-time-part [date-time]
  ((str/split date-time "T") 1))

(defn fill-table [data]
  (dotimes [i (count data)]
    (let [row (data i)]
      (.append (js/jQuery "tbody")
               (str "<tr>
                <th scope='row'" i "</th>"
                    "<td>" (:satelliteName row) "</td>"
                    "<td>" (get-time-part (:startFlybyTime row)) "</td>"
                    "<td>" (get-time-part (:endFlybyTime row)) "</td>"
                    "<td>" (:nakedEyeVisibilityMag row) "</td>"
                    "</tr>")))))

(defn get-nearby-historic-searches [lat lon]
  (go (let [response (<! (http/post "http://localhost:882/historic-searches" {:json-params {:lat lat
                                                                                            :lon lon}}))
            js-response (-> js/JSON
                            (.parse (:body response)))]
        (load-map l-map (js->clj js-response :keywordize-keys true) lat lon))))

(defn search [lat lon date time location]
  (go (let [response (<! (http/get "http://localhost:882/search" {:query-params {:location location
                                                                                 :date date
                                                                                 :time time
                                                                                 :lat lat
                                                                                 :lon lon}}))]
        (fill-table (js->clj (.parse js/JSON (:body response)) :keywordize-keys true)))))

(let [{lat "lat"
       lon "lon"
       date "date"
       time "time"
       location "location"} (:query (url (-> js/window .-location .-href)))]

  (set-heading (str/replace location #"[+]" " "))
  (go (get-nearby-historic-searches lat lon))

  (go (search
       lat
       lon
       date
       time
       (str/replace location #"[+]" " "))))
