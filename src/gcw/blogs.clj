(ns gcw.blogs
  (:require [net.cgrand.enlive-html :as html]))

(defrecord Blog [name home-url blacklist blacklist-patterns])

(defmulti post-title :name)
(defmulti post-author :name)
(defmulti post-date-published :name)
(defmulti post-body :name)
(defmulti post-archives :name)

(defn blacklisted-url? [blog url]
  (or (contains? (:blacklist blog) url)
      (reduce (fn [acc pattern]
                (if-let [x (re-find pattern url)]
                  (reduced true)
                  false))
              false
              (:blacklist-patterns blog))))


;############################################################################
; MELTING ASPHALT
;############################################################################

(def MA (map->Blog {:name "Melting Asphalt"
                    :home-url "https://meltingasphalt.com"
                    :blacklist #{"http://meltingasphalt.com/about"
                                 "http://meltingasphalt.com/subscribe"
                                 "http://meltingasphalt.com/what-im-reading"
                                 "http://meltingasphalt.com/goodies"
                                 "http://meltingasphalt.com"
                                 "http://meltingasphalt.com/archive"
                                 "http://meltingasphalt.com/copyright"
                                 "http://meltingasphalt.com/series"
                                 "http://meltingasphalt.com/contact"}
                    :blacklist-patterns []}))


(defmethod post-author "Melting Asphalt"
  [_ dom]
  (->> (html/select dom [:div.post :div.byline :a])
       first
       :content
       first))

(defmethod post-title "Melting Asphalt"
  [_ dom]
  (->> (html/select dom [:h1.post-title.entry-title])
       first
       :content
       first))

(defmethod post-date-published "Melting Asphalt"
  [_ _] nil)

(defmethod post-body "Melting Asphalt"
  [_ dom] (->> (html/select dom [:div.post-entry])
               first))

;############################################################################
; THE LAST PSYCHIATRIST
;############################################################################

(def TLP (map->Blog {:name "The Last Psychiatrist"
                     :home-url "https://thelastpsychiatrist.com/"
                     :blacklist #{"https://thelastpsychiatrist.com/"
                                  "https://thelastpsychiatrist.com/archives.html"
                                  "https://thelastpsychiatrist.com/about.html"}
                     :blacklist-patterns [#"\/\d{4}\/\d{2}\/$"
                                          #"^((?!\.html).)*$"]}))

(defmethod post-author "The Last Psychiatrist"
  [_ _] "The Last Psychiatrist")

(defmethod post-title "The Last Psychiatrist"
  [_ dom]
  (->> (html/select dom [:div#main :div#content :h1])
       first
       :content
       first))

(defmethod post-date-published "The Last Psychiatrist"
  [_ dom] (->> (html/select dom [:div#main :div#content :div.dated])
               first
               :content
               first))

(defmethod post-body "The Last Psychiatrist"
  [_ dom] (let [div (first (html/select dom [:div#main :div#content :div#more.entry-more]))]
            (assoc div :content (drop-last 5 (:content div)))))

;############################################################################
; SAM[]ZDAT
;############################################################################
(def SM (map->Blog {:name "Samzdat"
                    :home-url "https://samzdat.com/"
                    :blacklist #{"https://samzdat.com"
                                 "http://samzdat.com"
                                 "https://samzdat.com/archive"
                                 "https://samzdat.com/top-posts-and-introduction"
                                 "https://samzdat.com/2017/04/15/scraps-1-collapsejane-austenspenglercowen/lysenko"
                                 "https://samzdat.com/2017/04/15/scraps-1-collapsejane-austenspenglercowen/hypernorm-2"
                                 "https://samzdat.com/the-uruk-series"
                                 "https://samzdat.com/feed"
                                 "https://samzdat.com/about"}
                    :blacklist-patterns [#"\/page\/"
                                         #"\/category\/"
                                         #"\/author\/"]}))

(defmethod post-author "Samzdat"
  [_ _] "Lou Keep")

(defmethod post-title "Samzdat"
  [_ dom] (->> (html/select dom [:main :article :header :h1.entry-title])
               first
               :content
               first))

(defmethod post-date-published "Samzdat"
  [_ _] nil)

(defmethod post-body "Samzdat"
  [_ dom] (let [div (first (html/select dom [:main :article :div.entry-content]))]
            (assoc div :content (drop-last 2 (:content div)))))

;############################################################################
; HOTEL CONCIERGE
;############################################################################

;TODO(jecnpes): tumblr links are fucked up

(def HC (map->Blog {:name "Hotel Concierge"
                    :home-url "https://hotelconcierge.tumblr.com"
                    :blacklist "https://hotelconcierge.tumblr.com"
                    :blacklist-patterns []}))
(defmethod post-author "Hotel Concierge"
  [_ _] "Hotel Concierge")

(defmethod post-title "Hotel Concierge"
  [_ dom] (->> (html/select dom [:div.main :article :div.post-content :h2.title :a])
               first
               :content
               first))

(defmethod post-date-published "Hotel Concierge"
  [_ dom] nil)

(defmethod post-body "Hotel Concierge"
  [_ dom] (->> (html/select dom [:div.main :article :div.post-content])
               first))

;############################################################################
; SLATE STAR CODEX
;############################################################################

(def SSC (map->Blog {:name "Slate Star Codex"
                     :home-url "https://slatestarcodex.com"
                     :blacklist #{"https://slatestarcodex.com"
                                  "https://slatestarcodex.com/about"
                                  "https://slatestarcodex.com/archives"
                                  "https://slatestarcodex.com/top-posts"
                                  "https://slatestarcodex.com/feed"}
                     :blacklist-patterns [#"\/\d{4}\/\d{2}\/?$"
                                          #"\/\d{4}\/?$"
                                          #"\/category\/"
                                          #"\/comments\/"
                                          #"\/author\/"
                                          #"\/tag\/"]}))

(defmethod post-author "Slate Star Codex"
  [_ dom] (->> (html/select dom [:div.post :div.pjgm-postmeta :span.author.vcard :a])
               first
               :content
               first))

(defmethod post-title "Slate Star Codex"
  [_ dom] (->> (html/select dom [:div.post :h1.pjgm-posttitle])
               first
               :content
               first))

(defmethod post-date-published "Slate Star Codex"
  [_ dom] (->> (html/select dom [:div.post :div.pjgm-postmeta :a :span.entry-date])
               first
               :content
               first))

(defmethod post-body "Slate Star Codex"
  [_ dom] (->> (html/select dom [:div.post])
               first))

(defmethod post-archives "Slate Star Codex"
  [_ dom] (->> (html/select dom [:div#pjgm-content :div.page])
               first))
;############################################################################
; GLOBALS
;############################################################################

(def Network [MA SM TLP SSC])

(def Network-urls (map :home-url Network))

