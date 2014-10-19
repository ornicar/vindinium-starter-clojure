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
                 [org.clojure/tools.logging "0.3.1"]
                 [org.clojure/core.match "0.2.1"]
                 ]
  :aot [vindinium.core]
  :target-path "target/%s"
  :plugins [[lein-checkall "0.1.1"]
            [lein-localrepo "0.5.3"]
            [lein-marginalia "0.8.0"]]

  :profiles {:uberjar {:aot :all}})
