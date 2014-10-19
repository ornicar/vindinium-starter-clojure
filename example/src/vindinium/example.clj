(ns vindinium.example (:gen-class) (:require [vindinium.core :as vindinium]))

(defn -main
  []
  (vindinium/run-game-from-resource "config.edn"))
