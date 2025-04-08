(defproject betzin "0.1.0-SNAPSHOT"
  :description "API para gerenciar contas e apostas"
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [compojure "1.6.2"]
                 [ring/ring-defaults "0.3.3"]
                 [ring/ring-json "0.5.1"]
                 [cheshire "5.10.1"]
                 [clj-http "3.12.3"]]
  :plugins [[lein-ring "0.12.6"]]
  :ring {:handler betzin.handler/app}
  :profiles
  {:dev {:dependencies [[javax.servlet/servlet-api "2.5"]
                        [ring/ring-mock "0.4.0"]]}})
