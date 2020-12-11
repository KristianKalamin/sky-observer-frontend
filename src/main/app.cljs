(ns app
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [dommy.core :as dommy]
            [cljs-http.client :as http]
            [cljs.core.async :refer [<!]]))

(js/console.log "Hello World")

;(defn call-rest []
;  (:body @(client/request {:url "https://petstore.swagger.io/v2/pet/findByStatus?status=available"
 ;                          :method :get})))



(defn click-handler [e]
  (go (let [response (<! (http/get "https://petstore.swagger.io/v2/pet/findByStatus?status=available"))]
        (js/console.log (:status response))
        (js/console.log (map :login (:body response))))))

(dommy/listen! (dommy/sel1 :#hello) :click click-handler)



;https://petstore.swagger.io/v2/pet/findByStatus?status=available