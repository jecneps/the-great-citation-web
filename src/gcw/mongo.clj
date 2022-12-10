(ns gcw.mongo
  (:require [monger.core :as mg]
            [monger.collection :as mc]
            [gcw.blogs :as blog])
  (:import org.bson.types.ObjectId))

(def ^:dynamic *db-params* {:db-name "gcw" :db-host "127.0.0.1" :db-port 42667})

(defn mongo-name [s]
  (clojure.string/replace s #" " "+"))

(defn insert-post [blog post]
  (let [conn (mg/connect)
        db (mg/get-db conn (:db-name *db-params*))]
    (mc/insert db (mongo-name (:name blog)) post)))

;f replaces the value of the post in mongo
(defn update-all-posts [blog f]
  (let [conn (mg/connect)
        db (mg/get-db conn (:db-name *db-params*))]
    (loop [posts (mc/find-maps db (mongo-name (:name blog)))]
      (if (seq posts)
        (let [post (first posts)]
          (mc/update db (:name blog) {:_id (:_id post)} (f post))
          (recur (rest posts)))))))

(defn update-post [blog f m]
  (let [conn (mg/connect)
        db (mg/get-db conn (:db-name *db-params*))
        posts (mc/find-maps db (mongo-name (:name blog)) m)]
    (do
      (println (format "Post Count: %d" (count posts)))
      (map #(mc/update-by-id db (mongo-name (:name blog)) (:_id %) (f %)) posts))))

(defn read-fields [blog ks]
  (let [conn (mg/connect)
        db (mg/get-db conn (:db-name *db-params*))]
    (mc/find-maps db (mongo-name (:name blog)) {} ks)))

(defn get-by-url [blog urls]
  (let [conn (mg/connect)
        db (mg/get-db conn (:db-name *db-params*))]
    (map #(mc/find-maps db (mongo-name (:name blog)) {:url %}) urls)))