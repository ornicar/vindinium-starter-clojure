(defproject vindinium "0.1.0-SNAPSHOT"
  :description "Starter pack for the vindinium AI contest"
  :url "http://example.com/FIXME"
  :license {:name "MIT"
            :url "http://opensource.org/licenses/MIT"}
  :dependencies [
                 [org.clojure/clojure "1.5.1"]
                 [clj-http "0.7.8"]
                 [cheshire "5.3.1"]
                 [slingshot "0.10.3"]
                 [org.clojure/core.match "0.2.1"]
                 ]
  :main ^:skip-aot vindinium.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
