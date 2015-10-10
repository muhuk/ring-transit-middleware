(defproject ring-transit-middleware "0.1.0-SNAPSHOT"
  :description "Ring middleware for Transit request & responses."
  :url "https://github.com/muhuk/ring-transit-middleware"
  :license {:name "The MIT License"
            :url "http://opensource.org/licenses/MIT"}
  :dependencies [[com.cognitect/transit-clj "0.8.281" :exclusions [commons-codec]]
                 [org.clojure/clojure "1.7.0"]
                 [ring/ring-core "1.4.0"]]
  :plugins [[com.jakemccrary/lein-test-refresh "0.10.0"]
            [jonase/eastwood "0.2.1"]])
