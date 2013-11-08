(ns user
  (:use [io.aviso.twixt :only [get-asset-uris default-options]]
        [io.aviso.twixt exceptions tracker]
        ring.adapter.jetty))

(defn handler
  [request]
  (trace "Invoking handler (that throws exceptions)"
         ;; This will fail at some depth:
         (doall
             (get-asset-uris (:twixt request) "invalid-coffeescript.coffee"))))

(def app
  (wrap-with-twixt handler default-options true))

(defn launch []
  (let [server (run-jetty app {:port 8888 :join? false})]
    #(.stop server)))