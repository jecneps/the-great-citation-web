(ns gcw.mongo
		(:require [monger.core :as mg]
  										[monger.collection :as mc]
  										[gcw.blogs :as blog])
  (:import org.bson.types.ObjectId))

(def ^:dynamic *db-params* {:db-name "gcw" :db-host "127.0.0.1" :db-port 42667})

(defn insert-post [blog post]
		(let [conn (mg/connect)
								db (mg/get-db conn (:db-name *db-params*))]
											(mc/insert db (:name blog) post)))