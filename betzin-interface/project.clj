(defproject betzin-interface "0.1.0-SNAPSHOT"
  :description "Interface da conta Betzin"
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [clj-http "3.12.3"]
                 [cheshire "5.10.1"]]
  :main ^:skip-aot betzin-interface.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
