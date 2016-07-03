(defproject ring-transit-middleware "0.1.3-SNAPSHOT"
  :description "Ring middleware for Transit request & responses."
  :url "https://github.com/muhuk/ring-transit-middleware"
  :license {:name "The MIT License"
            :url "http://opensource.org/licenses/MIT"}
  :dependencies [[com.cognitect/transit-clj "0.8.285" :exclusions [commons-codec]]
                 [org.clojure/clojure "1.8.0"]
                 [ring/ring-core "1.5.0"]]
  :plugins [[lein-codox "0.9.5"]
            [com.jakemccrary/lein-test-refresh "0.10.0"]
            [jonase/eastwood "0.2.1"]]
  :profiles {:1.6 {:dependencies [[org.clojure/clojure "1.6.0"]]}
             :1.7 {:dependencies [[org.clojure/clojure "1.7.0"]]}}
  :codox {:metadata {:doc/format :markdown}
          :source-uri "https://github.com/muhuk/ring-transit-middleware/blob/master/{filepath}#L{line}"
          :output-path "target/doc"}
  :aliases {"all" ["with-profile" "dev:dev,1.6:dev,1.7"]})
