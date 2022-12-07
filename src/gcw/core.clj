(ns gcw.core
  (:gen-class)
  (:require [lambdaisland.uri :as uri]
  										[lambdaisland.uri.normalize :as norm]
  										[org.httpkit.client :as http]
  										[gcw.scraper :as scraper]
  										[gcw.blogs :as blog]
  										[net.cgrand.enlive-html :as html]
  										[clojure.xml :as xml])
  (:import
    [java.net URI]
    [javax.net.ssl
     SNIHostName SNIServerName SSLEngine SSLParameters]))

; (defn sni-configure
;   [^SSLEngine ssl-engine ^URI uri]
;   (let [^SSLParameters ssl-params (.getSSLParameters ssl-engine)]
;     (.setServerNames ssl-params [(SNIHostName. (.getHost uri))])
;     (.setUseClientMode ssl-engine true)
;     (.setSSLParameters ssl-engine ssl-params)))

; (def client (http/make-client {:ssl-configurer sni-configure}))

; (defrecord Post [title author blog date-published url link raw-scrape content links])



(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println "Hello, World!"))
