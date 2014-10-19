(ns
  ^{:doc "Contains functions useful for Vindinium bot implementations."}
  vindinium.core
  (:gen-class)
  (:require [slingshot.slingshot :refer [try+, throw+]])
  (:require [clojure.core.match :refer [match]])
  (:require [clojure.tools.logging :as log])
  (:require [clojure.edn :as edn])
  (:require [clojure.java.io :as io])
  (:require [clj-http.client :as http])
  (:import (java.awt Desktop))
  (:import (java.net URI)))

(defn random-bot
  "This function contains a stub implementation
  of a vindinium bot that logs its input and then
  chooses a random direction in which to go."
  [input]
  (do (log/info input)
      (first (shuffle ["north", "south", "east", "west", "stay"]))))

(defn at [[x y] tiles size]
  "Given an sequence of tiles `tiles` representing a grid of height and width
  `size`, returns the tile in position `[x y]`"
  (tiles (+ (* y size) x)))

; Because the (y,x) position of the server is inversed. We fix it to (x,y).
(defn- fix-pos [{:keys [x y]}] [y x])

(defn- fix-hero [hero]
  (-> hero
      (update-in [:pos] fix-pos)
      (update-in [:spawnPos] fix-pos)))

(defn- improve-input [input]
  (-> input
      (update-in [:hero] fix-hero)
      (update-in [:game :heroes] #(map fix-hero %))
      (update-in [:game :board :tiles] vec)))

(defn- parse-tile [tile]
  (match (vec tile)
         [\space \space] {:tile :air}
         [\# \#] {:tile :wall}
         [\[ \]] {:tile :tavern}
         [\$ \-] {:tile :mine}
         [\$ i] {:tile :mine :of i}
         [\@ i] {:tile :hero :id (Integer/parseInt (str i))}))

(defn- parse-tiles [tiles] (map parse-tile (partition 2 (seq tiles))))

(defn- parse-input [input] (update-in input [:game :board :tiles] parse-tiles))

(defn- request
  "makes a POST request and returns a parsed input"
  [url, params]
  (try+
    (-> (http/post url {:form-params params :as :json})
        :body
        parse-input
        improve-input)
    (catch map? {:keys [status body]}
      (log/info "[" status "] " body)
      (throw+))))


(defn- step [from bot]
  (loop [input from]
    (print ".")
    (let [next (request (:playUrl input) {:dir (bot input)})]
      (when-not (:finished (:game next)) (recur next)))))

(defrecord Config
  ^{:doc "This record represents configuration of a vindinium game to
   be read from an edn file.

  - `:mode` is a keyword - one of `:training` or `:arena`.
  - `:secret-key` is the secret key of the vindinium server
  as a string.
  - `:turns` is the number of turns to run, which
  will be ignored in arena mode.
  - `:server-url` is where the vindinium server is.
  - `:bot` is a symbol representing a bot function that takes
  an object representing the game state and returns a string.
  This function must be resolvable at the point of running
  the game from the config.
  representing the direction in which to go, which will be one of
    `north`, `east`, `south`, `west` or `stay`"}
  [mode secret-key turns server-url bot])

(defn training
  "This function submits the specified bot
  to the specified vindinium server using the specified
  secret key for a game in training mode lasting the specified
  number of turns.

  It is expected that the bot will be a function that takes an
  object representing the game state and returns a string representing
  the direction in which to go, which will be one of
  `north`, `east`, `south`, `west` or `stay`"
  [secret-key turns server-url bot]
  (let [input (request (str server-url "/api/training")
                       {:key secret-key :turns turns})]
    (let [viewUrl (:viewUrl input)] ((log/info
                                       "Starting training game" viewUrl)
                                     (future (.browse
                                               (Desktop/getDesktop)
                                               (URI. viewUrl)))
                                     (step input bot)
                                     (log/info
                                       "Finished training game"
                                       viewUrl)))))

(defn arena
  "This function submits the specified bot
  to the specified vindinium server using the specified
  secret key for the specified number of games in arena mode.

  It is expected that the bot will be a function that takes an
  object representing the game state in a given format
  and returns a string representing the direction in which
  to go, which will be one of
  `north`, `east`, `south`, `west` or `stay`"
  [secret-key games server-url bot]
  (loop [it 1]
    (let [p #(log/info "[" it "/" games "] " %)
          _ (p "Waiting for pairing...")
          input (request (str server-url "/api/arena") {:key secret-key})]
      (let [viewUrl (:viewUrl input)]
        ((p (str "Starting arena game " viewUrl))
         (future (.browse (Desktop/getDesktop) (URI. viewUrl)))
         (step input bot)
         (p (str "Finished arena game " viewUrl)))
        (when (< it games) (recur (inc it)))))))

(defn run-game-from-record
  "This function takes a `Config` record or map with similar
  fields and starts a vindinium game according to the configuration"
  [config]
  (match (:mode config)
         :arena (arena (:secret-key config)
                       1
                       (:server-url config)
                       (resolve (:bot config)))
         :training (training (:secret-key config)
                             (:turns config)
                             (:server-url config)
                             (resolve (:bot config)))))

(defn run-game-from-edn
  "This function takes an edn string representing a `Config` record
  or map with similar fields and starts a vindinium game according
  to the configuration"
  [edn-string]
  (-> edn-string edn/read-string run-game-from-record))

(defn run-game-from-resource
  "This function loads an edn string from the given resource representing
  a `Config` record or map with similar fields and starts a vindinium
  game according to the configuration"
  [resource-name]
  (-> resource-name io/resource run-game-from-edn))

(defn run-game-from-file
  "This function loads an edn string from the given resource representing
  a `Config` record or map with similar fields and starts a vindinium
  game according to the configuration"
  [file-uri]
  (-> file-uri io/file run-game-from-edn))
