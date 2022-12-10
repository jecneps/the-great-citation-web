(ns gcw.core
  (:gen-class)
  (:require [lambdaisland.uri :as uri]
            [lambdaisland.uri.normalize :as norm]
            [org.httpkit.client :as http]
            [gcw.scraper :as scraper]
            [gcw.blogs :as blogs]
            [gcw.mongo :as mongo]
            [monger.core :as mg]
            [monger.collection :as mc]
            [net.cgrand.enlive-html :as html]
            [clojure.xml :as xml])
  (:import
   [java.net URI]
   [javax.net.ssl
    SNIHostName SNIServerName SSLEngine SSLParameters]))


(defn write-to [d name]
  (with-open [w (clojure.java.io/writer name)]
    (binding [*out* w]
      (pr d))))

(defn read-from [name]
  (with-open [r (java.io.PushbackReader.
                 (clojure.java.io/reader name))]
    (binding [*read-eval* r]
      (read r))))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]

  (println "was"))
