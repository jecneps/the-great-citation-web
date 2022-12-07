(ns gcw.scraper
		(:require [net.cgrand.enlive-html :as html]
												[org.httpkit.client :as http]
												[lambdaisland.uri :as uri]
												[gcw.blogs :as blogs])
		(:import
    [java.net URI]
    [javax.net.ssl
     SNIHostName SNIServerName SSLEngine SSLParameters]))


(defn sni-configure
  [^SSLEngine ssl-engine ^URI uri]
  (let [^SSLParameters ssl-params (.getSSLParameters ssl-engine)]
    (.setServerNames ssl-params [(SNIHostName. (.getHost uri))])
    (.setUseClientMode ssl-engine true)
    (.setSSLParameters ssl-engine ssl-params)))

(def client (http/make-client {:ssl-configurer sni-configure}))


(defrecord PostData [title author blog date-published url raw-scrape content self-links in-network-links out-of-network-links])

(def scraper-cache (atom {}))

;##################################################################################
; UTILITIES
;##################################################################################

(defmulti cached-get class)

(defmethod cached-get java.lang.String
		[url] (if-let [scrape (get @scraper-cache url)]
																	scrape
																	(let [res (:body @(http/get url {:insecure? true :client client}))]
																			(swap! scraper-cache assoc url res)
																			res)))

(defmethod cached-get lambdaisland.uri.URI
		[uri] (cached-get (uri/uri-str uri)))

(defn nil-keys-except [m ks]
		(reduce (fn [acc k]
												(if (not (contains? ks k))
																(assoc acc k nil)
																acc))
										m
										(keys m)))


;##################################################################################
; URL/URI UTILS
;##################################################################################

(defn absolutify-uri [parent-uri uri]
		{:pre [(not (instance? java.lang.String uri))]}
		(if (uri/relative? uri)
						(assoc uri :host (:host parent-uri) :scheme (:scheme parent-uri))
						uri))

; (defn weburl? [url]
; 		(= "200"
; 					(:status @(http/get url {:insecure? true
; 																														:client client}))))

; (def weburi? (comp weburl? uri/uri-str))

(defn same-host? [u1 u2]
		(= (:host (uri/parse u1)) (:host (uri/parse u2))))

(defn trash-href? [href]
		(or
				(not (instance? java.lang.String href))
				(= href "")
				(re-find #"^javascript:" href)
				(re-find #"^#" href)))

(defn non-html-resource? [uri]
		(if-let [path (:path uri)]
										(if-let [[_ file] (re-find #"\.(.*)$" path)]
																			(not (contains? #{"html" "pdf"} file))))) ;TODO(jecnpes): possibly extend? .txt?
;
; still un-normalized, uri could point to a fragment,
(defn href->uri [parent-uri href]
		(if (trash-href? href)
						nil
						(let [uri (absolutify-uri parent-uri (uri/parse href))]
																	(if (non-html-resource? uri)
																					nil
																					uri))))

; assumes uri is a post, keeps only post centric info and normalizes /'s
(defn normalize-uri [uri]
		(-> (assoc uri 
												:host (clojure.string/replace (:host uri) #"/*$" "")
												:scheme "http" ;TODO(jecneps): will this be a problem later for scraping? Is that what :insecure? is for?
												:path (let [path (:path uri)
																								s (if (not= \/ (first path))
																														(str "/" path)
																														path)]
																								(clojure.string/replace s #"/*$" "")))
							(nil-keys-except #{:host :scheme :path})))


;##################################################################################
; PARSING SCRAPES
;##################################################################################

(defn url->dom [url]
		(html/html-snippet (cached-get url)))

(def uri->dom (comp url->dom uri/uri-str))

(defn extract-uris [dom parent-uri]
		(->> (html/select dom [:a])
							(map #(get-in % [:attrs :href]))
							(reduce (fn [acc href]
																	(if-let [uri (href->uri parent-uri href)]
																									(conj acc uri)
																									acc))
															[])))

(defn extract-urls [dom parent-url]
		(->> (extract-uris dom (uri/parse parent-url))
							(map uri/uri-str)))

(defn uri->group-key [network parent-uri uri]
		(cond
				(= (:host parent-uri) (:host uri)) :self-links
				(contains? network (:host uri)) :in-network-links
				:default :out-of-network-links))

(defn partition-uris [network parent-uri uris]
		(group-by (partial uri->group-key network parent-uri) uris))

(defn deduplicate-uris [network parent-uri uris]
		(let [m (partition-uris network parent-uri uris)]
				{:self-links (into #{} (map normalize-uri (:self-links m)))
					:in-network-links (into #{} (map normalize-uri (:in-network-links m)))
					:out-of-network-links (into #{} (:out-of-network-links m))}))

(defn deduplicate-urls [network parent-url urls]
		(let [uris (deduplicate-uris (into #{} (map uri/parse network))
																															(uri/parse parent-url)
																															(map uri/parse urls))]
				(reduce (fn [acc k]
																(update acc k #(map uri/uri-str %))) 
												uris
												(keys uris))))

(defn recursively-scrape-uri [base-uri seed-uris]
		(loop [frontier (into #{} (conj seed-uris base-uri)) visited #{}]
				(if (empty? frontier)
								visited
								(let [cur-uri (first frontier)
														dom 				(uri->dom cur-uri)]
														(recur (->> (extract-uris dom base-uri) 
																										(map normalize-uri)
																										(filter (partial same-host? base-uri)) ;TODO(jecneps): uri/url error for same-host?
																										(filter #(not (contains? (conj visited cur-uri))))
																										(into (disj frontier cur-uri)))
																					(conj visited cur-uri))))))

(defn recursively-scrape-url [base-url seed-urls]
		(recursively-scrape-uri (normalize-uri (uri/parse base-url))
																										(map (comp normalize-uri uri/uri-str) seed-urls)))

(defn clean-post-content [dom]
		(->> dom
							html/text
							clojure.string/trim))

(defn url->PostData [blog url]
		(let [scrape (cached-get url)
								dom (url->dom url)
								content-dom (blogs/post-body blog dom)]
				(map->PostData
						(merge
								{:title  (blogs/post-title blog dom)
									:author	(blogs/post-author blog dom)
									:blog			(:name blog)
									:date-published (blogs/post-date-published blog dom)
									:url url
									:raw-scrape scrape
									:content (clean-post-content content-dom)
									}
									(update (deduplicate-urls (into #{} (map :home-url blogs/Network)) 
																																			url 
																																			(extract-urls content-dom url))
																	:self-links
																	(fn [urls] (filter #(not (blogs/blacklisted-url? blog %)) urls)))))))

