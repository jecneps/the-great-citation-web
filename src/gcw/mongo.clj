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

;f replaces the value of the post in mongo
(defn update-all-posts [blog f]
		(let [conn (mg/connect)
								db (mg/get-db conn (:db-name *db-params*))]
								(loop [posts (mc/find-maps db (:name blog))]
															(if (not (empty? posts))
																			(let [post (first posts)]
																								(mc/update db (:name blog) {:_id (:_id post)} (f post))
																								(recur (rest posts)))))))

(defn read-fields [blog ks]
		(let [conn (mg/connect)
								db (mg/get-db conn (:db-name *db-params*))]
								(mc/find-maps db (:name blog) {} ks)))

(defn get-by-url [blog urls]
		(let [conn (mg/connect)
								db (mg/get-db conn (:db-name *db-params*))]
								(map #(mc/find-maps db (:name blog) {:url %}) urls)))