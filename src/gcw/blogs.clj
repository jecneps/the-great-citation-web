(ns gcw.blogs
		(:require [net.cgrand.enlive-html :as html]))

(defrecord Blog [name home-url blacklist blacklist-patterns])

(defmulti post-title :name)
(defmulti post-author :name)
(defmulti post-date-published :name)
(defmulti post-body :name)

(defn blacklisted-url? [blog url]
		(or (contains? (:blacklist blog) url)
						(reduce (fn [acc pattern] 
																		(if-let [x (re-find pattern url)]
																										(reduced true)
																										false))
														false
														(:blacklist-patterns blog))))




(def MA (map->Blog {:name "Melting Asphalt" 
																				:home-url "https://meltingasphalt.com/"
																				:blacklist #{"https://meltingasphalt.com/about/"
																																	"https://meltingasphalt.com/subscribe/"
																																	"https://meltingasphalt.com/what-im-reading/"
																																	"https://meltingasphalt.com/goodies/"
																																	"https://meltingasphalt.com/"
																																	"https://meltingasphalt.com/archive/"
																																	"https://meltingasphalt.com/copyright/"
																																	"https://meltingasphalt.com/series/"}
																				:blacklist-patterns []}))


(defmethod post-author "Melting Asphalt"
		[_ dom]
		(->> (html/select dom [:div.post :div.byline :a])
							first
							:content
							first))

(defmethod post-title "Melting Asphalt"
		[_ dom]
		(->> (html/select dom [:div.post :h1.post-title])
							first
							:content
							first))

(defmethod post-date-published "Melting Asphalt"
		[_ _] nil)

(defmethod post-body "Melting Asphalt"
		[_ dom] (->> (html/select dom [:div.post :div.post-entry])
															first))

;############################################################################
; GLOBALS
;############################################################################

(def Network [MA])