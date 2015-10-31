(defproject vindinium "0.1.0-SNAPSHOT"
  :description "Starter pack for the vindinium AI contest"
  :url "http://example.com/FIXME"
  :license {:name "MIT"
            :url "http://opensource.org/licenses/MIT"}
  :dependencies [
                 [org.clojure/clojure "1.7.0"]
                 [clj-http "2.0.0"]
                 [cheshire "5.5.0"]
                 [slingshot "0.12.2"]
                 [org.clojure/core.match "0.2.2"]
                 ]
  :main ^:skip-aot vindinium.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
