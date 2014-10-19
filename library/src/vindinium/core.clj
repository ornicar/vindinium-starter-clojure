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

(defn- at [[x y] tiles size]
  "Given an sequence of tiles `tiles` representing a grid of height and width
  `size`, returns the tile in position `[x y]`"
  (let [out (tiles (+ (* y size) x))] out))

(defrecord Hero
  ^{:doc "A record representing a hero.

         -`:id` -  the hero's id as a string.
         -`:name` - the hero's name as a string.
         -`:elo` - the elo of the hero - an integral value or nil.
         -`:pos` - a vector of two members representing the hero's current
         position.
         -`:life` - how many life points the hero has as an integral value.
         -`:gold` - how much gold the hero has as an integral value.
         -`:mine-count` - how many mines the hero has
         -`:spawn-pos` - a vector of two members representing the spawn
         position of the hero.
         -`:crashed` - a boolean representing whether the hero has crashed or
         not.`"}
  [id name elo pos life gold mine-count spawn-pos crashed])

(defrecord GameState
  ^{:doc "A record representing the state of the game.
      
         -`:dimensions` - a vector of two members representing the dimensions
         of the board.
         -`:my-hero` - a `Hero` record representing your hero.
         -`:heroes` - a sequence of `Hero` records representing all the heroes.
         -`:current-turn` - an integral value representing the number of the
         current turn.
         -`:max-turns` - an integral value representing the total number of
         turns in this game.
         -`:finished` - a boolean value indicating whether or not the game is
         over.
         -`:tiles` - a vector of vectors of objects representing tiles."}
  [dimensions my-hero heroes current-turn max-turns finished tiles])

(defrecord Tile
  ^{:doc "A record representing a board tile.

         -`:tile` - the tile type. Can be one of `:wall`, `:air`, `:hero`,
         `:mine`, `:tavern`
         -`:of` - if the tile represents an owned mine, the id of
         the hero owning the mine - otherwise `nil`.
         -`:id` - if the tile represents a hero, the id of the hero
         owning the mine - otherwise `nil`."}
  [tile of id])

(defn- hero-map-to-record [hero]
  (->Hero (:id hero) (:name hero) (:elo hero) (:pos hero) (:life hero)
          (:gold hero) (:spawnPos hero) (:mineCount hero)
          (:crashed hero)))

(defn- tile-map-to-record [tile]
  (->Tile (:tile tile) (:of tile) (:id tile)))

(defn- game-state-map-to-record [game-state]
  (let [size (-> game-state :game :board :size)
        out (->GameState [size size]
                         (-> game-state :hero hero-map-to-record)
                         (map hero-map-to-record (-> game-state :game :heroes))
                         (-> game-state :game :turn)
                         (-> game-state :game :maxTurns)
                         (-> game-state :game :board :finished)
                         (and size (let [to-vector (partial into [])
                                         coords (-> size range to-vector)] (map
                                                      (fn [x]
                                                        (map
                                                          (fn [y]
                                                            (tile-map-to-record
                                                              (at [x y]
                                                                (-> game-state
                                                                    :game
                                                                    :board
                                                                    :tiles)
                                                                  size)))
                                                        coords)) coords))))]
    out))

(defn random-bot
  "This function contains a stub implementation
  of a vindinium bot that logs its input and then
  chooses a random direction in which to go."
  [input]
  (do (log/info input)
      (first (shuffle ["north", "south", "east", "west", "stay"]))))

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
    (let [next (request (:playUrl input)
                        {:dir (bot (game-state-map-to-record input))})]
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
  a GameState record and returns a string.
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

  It is expected that the bot will be a function that takes a
  GameState record and returns a string representing
  the direction in which to go, which will be one of
  `north`, `east`, `south`, `west` or `stay`"
  [secret-key turns server-url bot]
  (let [input (request (str server-url "/api/training")
                       {:key secret-key :turns turns})]
    (let [viewUrl (:viewUrl input)] (log/info
                                       "Starting training game" viewUrl)
                                     (future (.browse
                                               (Desktop/getDesktop)
                                               (URI. viewUrl)))
                                     (step input bot)
                                     (log/info
                                       "Finished training game"
                                       viewUrl))))

(defn arena
  "This function submits the specified bot
  to the specified vindinium server using the specified
  secret key for the specified number of games in arena mode.

  It is expected that the bot will be a function that takes an
  GameState record and returns a string representing the
  direction in which to go, which will be one of
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
                             (resolve (:bot config)))
         :else (log/error "[" (:mode config) "] is not a valid mode"
                          "(taken from config ["
                          config "]")))

(defn run-game-from-edn
  "This function takes an edn string representing a `Config` record
  or map with similar fields and starts a vindinium game according
  to the configuration"
  [edn-string]
  (let [read-edn-from-config #(edn/read-string
                               {:readers
                                {'vindinium.core.Config map->Config}} %)]
  (-> edn-string read-edn-from-config run-game-from-record)))

(defn run-game-from-resource
  "This function loads an edn string from the given resource representing
  a `Config` record or map with similar fields and starts a vindinium
  game according to the configuration"
  [resource-name]
  (-> (doto resource-name println) io/resource slurp run-game-from-edn))

(defn run-game-from-file
  "This function loads an edn string from the given resource representing
  a `Config` record or map with similar fields and starts a vindinium
  game according to the configuration"
  [file-uri]
  (-> file-uri io/file slurp run-game-from-edn))
