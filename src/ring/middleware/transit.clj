(ns ring.middleware.transit
  (:require [cognitect.transit :as t]
            [ring.util.response :refer [content-type]])
  (:import [java.io ByteArrayInputStream ByteArrayOutputStream]))


(def ^:private aleph-available?
  (try
    (require '[aleph.http])
    true
    (catch java.io.FileNotFoundException _ false)))


(def chain (if aleph-available?
             (find-var 'manifold.deferred/chain)
             identity))


(declare -decode-request
         -encode-response
         parse-transit-content-type
         ring-response-body?)


(def ^:private re-ct
  #"^application/transit\+(json|json-verbose|msgpack)(;\s*charset=([-\w]+))?")


(defn decode
  ([s]
   (decode s nil))
  ([^String s options]
   {:pre [(string? s)]}
   (let [encoding (:encoding options :json)
         options (dissoc options :encoding)]
     (-> (.getBytes s)
         (ByteArrayInputStream.)
         (t/reader encoding options)
         (t/read)))))


(defn encode
  ([v]
   (encode v nil))
  ([v options]
   {:post [(string? %)]}
   (let [buffer-size (get options :buffer-size 1024)
         encoding (get options :encoding :json)
         options (dissoc options
                         :buffer-size
                         :encoding)
         out (ByteArrayOutputStream. buffer-size)
         _ (-> out
               (t/writer encoding options)
               (t/write v))
         result (.toString out)]
     (.reset out)
     result)))


(defn wrap-transit
  ([handler] (wrap-transit handler nil))
  ([handler options]
   (let [req-opts (select-keys options [:reader])
         res-opts (select-keys options [:writer])]
     (fn [request]
       (-> request
           (-decode-request req-opts)
           (handler)
           (chain #(-encode-response % res-opts)))))))


(defn -decode-request [request options]
  ;; TODO: Document that :params is merged only if data is a map
  (let [{:keys [transit? encoding charset]} (parse-transit-content-type request)]
    (if-not transit?
      request
      (let [options (assoc options
                      :encoding (or encoding (:encoding options)))
            body (slurp (:body request) :encoding charset)
            transit-params (decode body options)
            request' (assoc request
                       :body body
                       :transit-params transit-params)]
        (assert (contains? #{:json :msgpack nil} encoding))
        (if (map? transit-params)
          (update-in request' [:params] merge transit-params)
          request')))))


(defn -encode-response [response options]
  (let [{:keys [transit? encoding charset] :as ct} (parse-transit-content-type response)]
    (if (or (false? transit?)
            (and (nil? transit?)
                 (ring-response-body? response)))
      response
      (-> response
          (cond->
           (nil? (:content-type ct))
           (content-type (format "application/transit+%s; charset=%s"
                                 (name (or encoding
                                           (get :encoding options :json)))
                                 charset)))
          (update-in [:body] encode options)))))


(defn- parse-transit-content-type [r]
  (let [ct (or (get-in r [:headers "content-type"])
               (get-in r [:headers "Content-Type"]))]
    (if (nil? ct)
      {:charset "utf-8"}
      (let [[ct-transit encoding _ charset] (re-find re-ct ct)
            charset (or charset "utf-8")]
        (cond-> {:content-type ct
                 :transit? false}
                (some? ct-transit) (assoc
                                     :transit? true
                                     :encoding (keyword encoding)
                                     :charset charset))))))


(defn- ring-response-body? [response]
  (let [body (:body response)]
    (or (string? body)
        (instance? java.io.InputStream body)
        (instance? java.io.File body)
        (instance? clojure.lang.ISeq body))))
