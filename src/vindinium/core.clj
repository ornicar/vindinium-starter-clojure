(ns vindinium.core
  (:gen-class)
  (:use [slingshot.slingshot :only [throw+ try+]]))

(require '[clj-http.client :as http])

; (def server-url "http://vindinium.jousse.org")
; (def server-key "mjhxh0om")
(def server-url "http://24hcodebot.local")
(def server-key "f3b1fm3m")

(defn get-dir [input]
  "north")

(defn finish [input]
  (println (str "Finished game " (:viewUrl input))))

(defn request [url, params]
  (try+
    (:body (http/post url {:form-params params
                           :as :json}))
    (catch [] {:keys [status body]}
      (println (str "[" status "] " body))
      (throw+))))

(defn step [input]
  (if (:finished (:game input))
    (finish input)
    ((print ".")
     (step (request (:playUrl input) {:dir (get-dir input)})))))

(defn training [turns]
  (step (request (str server-url "/api/training")
                 {:key server-key :turns turns})))

(defn -main [& args]
  (training 20))
