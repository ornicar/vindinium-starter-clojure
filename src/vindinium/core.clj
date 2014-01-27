(ns vindinium.core
  (:gen-class)
  (:use [slingshot.slingshot :only [try+, throw+]])
  (:use [clojure.core.match :only (match)]))

(require '[clj-http.client :as http])

; (def server-url "http://vindinium.org")
; (def server-key "mySecretKey")
(def server-url "http://24hcodebot.local")
(def server-key "f3b1fm3m")

(defn bot [input]
  "Implement this function to create your bot!"
  ; (prn input)
  (first (shuffle ["north", "south", "east", "west", "stay"])))

(defn parse-tile [tile]
  (match (vec tile)
         [\space \space] {:tile :air}
         [\# \#] {:tile :wall}
         [\[ \]] {:tile :tavern}
         [\$ \-] {:tile :mine}
         [\$ i] {:tile :mine :of i}
         [\@ i] {:tile :hero :id (Integer/parseInt (str i))}))

(defn parse-tiles [tiles] (map parse-tile (partition 2 (seq tiles))))

(defn parse-input [input] (update-in input [:game :board :tiles] parse-tiles))

(defn request [url, params]
  "makes a POST request and returns a parsed input"
  (try+
    (parse-input (:body (http/post url {:form-params params :as :json})))
    (catch [] {:keys [status body]}
      (println (str "[" status "] " body))
      (throw+))))

(defn step [from]
  (loop [input from]
    (print ".")
    (let [next (request (:playUrl input) {:dir (bot input)})]
      (if (:finished (:game next)) (println "") (recur next)))))

(defn training [turns]
  (let [input (request (str server-url "/api/training") {:key server-key :turns turns})]
    (println (str "Starting training game " + (:viewUrl input)))
    (step input)
    (println (str "Finished training game " + (:viewUrl input)))))

(defn arena [games]
  (loop [it 1]
    (let [p #(println (str "[" it "/" games "] " %))
          _ (p "Waiting for pairing...")
          input (request (str server-url "/api/arena") {:key server-key})]
      (p (str "Starting arena game " (:viewUrl input)))
      (step input)
      (p (str "Finished arena game " (:viewUrl input)))
      (when (< it games) (recur (+ it 1))))))

(def usage
  "Usage:
   training <number-of-turns>
   arena <number-of-games")

(defn -main [& args]
  (match (vec args)
         ["training", nb] (training nb)
         ["arena", nb] (arena nb)
         :else (println usage)))
