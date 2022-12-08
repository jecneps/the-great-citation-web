(ns gcw.scraper
		(:require [net.cgrand.enlive-html :as html]
												[org.httpkit.client :as http]
												[lambdaisland.uri :as uri]
												[gcw.blogs :as blogs])
		(:import
    [java.net URI InetAddress]
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
(defn ip-string? [s]
			;(println (str "ip-string?: s=" s " type=" (type s)))
		 (re-matches #"\d*?\.\d*?\.\d*?\.\d*?" s))

(defn ip->bytes [ip]
		(->> (clojure.string/split ip #"\.")
							(map #(. Integer parseInt %))
							(map unchecked-byte)
							byte-array))

(defn ip-uri->dn [parent-uri uri]
		(if (nil? (:host uri)) ;seems like this is my main choke point for filtering out bad links. I will lose some real ones though (missing "//")
						(do (println (format "Bad URL: %s did not have a host when parsed." (uri/uri-str uri)))
											nil)
						(if-let [ip (ip-string? (:host uri))]
										(if (= ip (.getHostAddress (InetAddress/getByName (:host parent-uri))))
														(assoc uri :host (:host parent-uri))
														uri)
										uri)))

(defn ip-url->dn [parent-url url]
		(uri/uri-str (ip-uri->dn (uri/parse parent-url) (uri/parse url))))

(defn absolutify-uri [parent-uri uri]
		{:pre [(not (instance? java.lang.String uri))]}
		(if (uri/relative? uri)
						(assoc uri :host (:host parent-uri) :scheme (:scheme parent-uri))
						(ip-uri->dn parent-uri uri)))

(defn same-host-url? [u1 u2]
		(= (:host (uri/parse u1)) (:host (uri/parse u2))))

(defn same-host-uri? [u1 u2]
		;(println (str "same-host-uri?: h1=" (uri/uri-str u1) " h2=" (uri/uri-str u2)))
		(if-let [ip (ip-string? (:host u2))] ;TODO(jecneps): better matching (IPv6 and such)
										;MA links are sometimes ip addresses instead of domain names
										(= ip (.getHostAddress (InetAddress/getByName (:host u1)))) 
										(= (:host u1) (:host u2))))

(defn trash-href? [href]
		(or
				(not (instance? java.lang.String href))
				(= href "")
				(re-find #"^mailto:" href)
				(re-find #"^javascript:" href)
				(re-find #"^#" href)))

(defn non-html-resource? [uri]
		(if-let [path (:path uri)]
										(if-let [[_ file] (re-find #"\.(.*)$" path)]
																			(not (contains? #{"html" "pdf"} file))))) ;TODO(jecnpes): possibly extend? .txt?

; still un-normalized, uri could point to a fragment,
(defn href->uri [parent-uri href]
		(if (trash-href? href)
						nil
						(if-let [uri (absolutify-uri parent-uri (uri/parse href))]
														(if (non-html-resource? uri)
																		nil
																		uri))))

(defmulti schema identity)
(defmethod schema "meltingasphalt.com"
		[_] "http")
(defmethod schema "thelastpsychiatrist.com"
		[_] "https")
(defmethod schema "samzdat.com"
		[_] "https")

; assumes uri is a post, keeps only post centric info and normalizes /'s
(defn normalize-uri [uri]
		(-> (assoc uri 
												;:host (clojure.string/replace (:host uri) #"/*$" "") I think i don't need this
											;	:scheme (schema (:host uri))
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
				{:self-links (disj (into #{} (map normalize-uri (:self-links m))) parent-uri)
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
		; (println base-uri)
		; (println seed-uris)
		(loop [frontier (into #{} (conj seed-uris base-uri)) visited #{}]
				(if (empty? frontier)
								visited
								(let [cur-uri (first frontier)
														dom 				(uri->dom cur-uri)]
														(recur (let [vis (conj visited cur-uri)]
																										(reduce (fn [acc uri]
																																						(if (not (contains? vis uri))
																																										(if (same-host-uri? base-uri uri)
																																														(conj acc (normalize-uri uri))
																																														acc)
																																										acc)) 
																																		(disj frontier cur-uri) 
																																		(extract-uris dom cur-uri)))
														; (->> (extract-uris dom cur-uri) 
														; 																										(map normalize-uri)
														; 																										(filter (partial same-host-uri? base-uri))
														; 																										(filter #(not (contains? (conj visited cur-uri) %)))
														; 																										(into (disj frontier cur-uri)))
																					(conj visited cur-uri))))))

(defn recursively-scrape-url [base-url seed-urls]
		(->> (recursively-scrape-uri (normalize-uri (uri/parse base-url))
																															(map (comp normalize-uri uri/parse) seed-urls))
							(map uri/uri-str)))

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

(defn blog->PostData [blog]
		(->> (recursively-scrape-url (:home-url blog) #{(:home-url blog)})
							(into #{})
							(filter #(not (blogs/blacklisted-url? blog %)))
							(map (partial url->PostData blog))))

(defn archive->uris [blog uri]
		(as-> (uri->dom uri) $
								(blogs/post-archives blog $)
								(extract-uris $ uri)))

(defn uris->post-uris [blog uris]
		(reduce (fn [acc uri]
														(if (same-host-uri? (uri/parse (:home-url blog)) uri)
																		(let [normed (normalize-uri uri)]
																							(if (not (blogs/blacklisted-url? blog  (uri/uri-str normed)))
																											(conj acc normed)
																											acc))
																			acc))
											#{}
											uris))

(defn archive->post-uris [blog uri]
		(uris->post-uris blog (archive->uris blog uri)))

;##################################################################################
; MANUAL DATA FIXES
;##################################################################################

(defn fix-ip-links [blog post]
		(let [self-links (:self-links post) out-links (:out-of-network-links post)
								ip-links (filter #(ip-string? (:host (uri/parse %))) out-links)]
				(assoc post 
											:self-links 
											(into self-links (map (partial ip-url->dn (:home-url blog))
																																	ip-links))
											:out-of-network-links (filter #(not (contains? (into #{} ip-links) %)) out-links))))

(defn t [] (println "test"))
