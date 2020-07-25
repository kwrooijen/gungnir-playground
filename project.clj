(defproject gungnir-playground "0.1.0-SNAPSHOT"
  :description "Example code for the Gungnir library"
  :url "https://github.com/kwrooijen/gungnir-playground"
  :license {:name "MIT"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [kwrooijen/gungnir "0.0.1-SNAPSHOT"]
                 [buddy/buddy-hashers "1.4.0"]
                 [ragtime "0.8.0"]]
  :main ^:skip-aot gungnir-playground.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}})
