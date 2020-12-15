(ns result
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs-http.client :as http]
            [cemerick.url :refer (url)]
            [clojure.string :as str]
            [cljs.core.async :refer [<!]]))

(def tile-url
  "https://api.maptiler.com/maps/basic/{z}/{x}/{y}.png?key=Y7YjJp1Xw0igXnskUdzY")

(def l-map (-> js/L (.map "proxy-map")))
(def lat-atom (atom ""))
(def lon-atom (atom ""))

(defn load-map [data]
  (let [m (-> l-map
              (.setView (array @lat-atom @lon-atom) 10))
        tile1 (-> js/L (.tileLayer tile-url
                                   #js{:attribution "<a href='https://www.maptiler.com/copyright/' target='_blank'>&copy; MapTiler</a> <a href='https://www.openstreetmap.org/copyright' target='_blank'>&copy; OpenStreetMap contributors</a>"
                                       :tileSize 512
                                       :zoomOffset -1
                                       :minZoom 1}))]

    (-> tile1
        (.addTo m)))

  ; loop and set markers
  (doseq [mark data]
    (let [{lat :lat
           lon :lon} mark]
      (-> js/L
          (.marker (array lat lon))
          (.addTo l-map)))))

(defn set-heading [location]
  (-> (js/jQuery ".heading")
      (.val (str "All flybys over " location))))

(defn get-time-part [date-time]
  ((str/split date-time "T") 1))

(defn fill-table [data]
  (go (dotimes [i (count data)]
        (let [row (data i)]
          (-> (js/jQuery "tbody")
              (.append
               (str "<tr>
                <th scope='row'" i "</th>"
                    "<td>" (:satelliteName row) "</td>"
                    "<td>" (get-time-part (:startFlybyTime row)) "</td>"
                    "<td>" (get-time-part (:endFlybyTime row)) "</td>"
                    "<td>" (:nakedEyeVisibilityMag row) "</td>"
                    "</tr>")))))))

(defn get-nearby-historic-searches [lat lon]
  (go (let [response (<! (http/post "http://localhost:882/historic-searches" {:json-params {:lat lat
                                                                                            :lon lon}}))
            js-response (-> js/JSON
                            (.parse (:body response)))]
        (load-map (js->clj js-response :keywordize-keys true)))))

(defn search [lat lon date time location]
  (go (let [response (<! (http/get "http://localhost:882/search" {:query-params {:location location
                                                                                 :date date
                                                                                 :time time
                                                                                 :lat lat
                                                                                 :lon lon}}))
            js-response (-> js/JSON
                            (.parse (:body response)))]
        (fill-table (js->clj js-response :keywordize-keys true)))))

(let [{lat "lat"
       lon "lon"
       date "date"
       time "time"
       location "location"} (:query (url (-> js/window .-location .-href)))]

  (reset! lat-atom lat)
  (reset! lon-atom lon)
  (get-nearby-historic-searches lat lon)

  (go (search
       lat
       lon
       date
       time
       (str/replace location #"[+]" " "))))
