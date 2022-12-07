(ns gcw.core
  (:gen-class)
  (:require [lambdaisland.uri :as uri]
  										[lambdaisland.uri.normalize :as norm]
  										[org.httpkit.client :as http]
  										[gcw.scraper :as scraper]
  										[gcw.blogs :as blogs]
  										[gcw.mongo :as mongo]
  										[net.cgrand.enlive-html :as html]
  										[clojure.xml :as xml])
  (:import
    [java.net URI]
    [javax.net.ssl
     SNIHostName SNIServerName SSLEngine SSLParameters]))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (mongo/insert-post blogs/MA (scraper/url->PostData blogs/MA "http://meltingasphalt.com/neurons-gone-wild/")))
