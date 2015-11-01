;; Copyright © 2015 Atamert Ölçgen
;;
;; Distributed under the The MIT License
;; http://opensource.org/licenses/MIT

(ns ring.middleware.transit
  "Ring middleware for Transit requests & responses.

  For basic usage you just need to add [[wrap-transit]]
  middleware.

  #### Usage Example:

      (require '[ring.middleware.transit :refer [wrap-transit]])


      (defn handler [request]
        (let [username (get-in request [:params :username])]
          {:hello username}))


      (defn app
        (-> handler
            (wrap-transit)))


  [[encode]] & [[decode]] are provided for reusing of options to
  [[wrap-transit]] when encoding/decoding Transit outside of
  HTTP requests/responses. When using WebSockets or
  communicating with other services.

  If you want to write a custom middleware based on this code
  take a look at `-decode-request` & `-encode-response` functions.
  They are not documented.
  "
  (:require [cognitect.transit :as t]
            [ring.util.response :refer [content-type]])
  (:import [java.io ByteArrayInputStream ByteArrayOutputStream]))


(def ^:private aleph-available?
  (try
    (require '[aleph.http])
    true
    (catch java.io.FileNotFoundException _ false)))


(def ^:private chain
  (if aleph-available?
    (find-var 'manifold.deferred/chain)
    (fn [v f] (f v))))


(declare -decode-request
         -encode-response
         parse-transit-content-type
         ring-response-body?)


(def ^:private re-ct
  #"^application/transit\+(json|json-verbose|msgpack)(;\s*charset=([-\w]+))?")


(defn decode
  "Decode string Transit data.

  #### Parameters:

  s
  :   Transit data. Note that unlike `cognitect.transit/reader`
  this function takes a `String`.

  options
  :   Optional parameter. This is a map that will be passed
  to `cognitect.transit/reader` as its third argument.
  Additionally `decode`'s `options` map can contain `:encoding`:

      :encoding
      :   Transit reader's encoding. Default is `:json`.
      Passed to `reader` as its second argument.

      An example options map:

          {:encoding :json
           :handlers {Foo (FooHandler.)}
           :default-handler (DefaultHandler.)}

      `:encoding` key stripped from `options` before calling `reader`.
  "
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
  "Encode some value into string Transit data.

  #### Parameters:

  v
  :   Value to be encoded.

  options
  :   Optional parameter. This is a map that will be passed
  to `cognitect.transit/writer` as its third argument.
  Additionally there are two more keys accepted:

      :buffer-size
      :   Size of the buffer of the output stream, in bytes.
      Default is `1024`.

      :encoding
      :   Transit writer's encoding. Default is `:json`.
      Passed to `reader` as its second argument.

      An example options map:

          {:buffer-size 4096
           :encoding :json-verbose
           :handlers {Foo (FooHandler.)}}

      `:buffer-size` & `:encoding` keys are stripped from
      `options` before calling `writer`.
  "
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
  "Decodes Transit requests and encodes Transit responses.

  #### Parameters:

  handler
  :   Ring handler to wrap.

  options
  :   Optional parameter. A map of options that can contain a
  `:reader` and a `:writer` keys which correspond to options
  to be passed to [[decode]] and [[encode]] respectively.

      `[:reader :encoding]` will always be overwritten using the
  `Content-Type` header of the request.

  #### Transit Requests

  Decoded Transit messages can be accessed through request's
  `:transit-params` key. If the decoded object is a map, it will
  be also be merged with request's `:params`.

  For Transit requests, `:body` is read into a string and is
  available to downstream.


  #### Transit Responses

  If there is no `Content-Type` header, anything but the types ring
  accepts as valid response bodies are encoded. If `Content-Type` is
  present it overrides the type of `:body`.


  | Content Type Header | Response Type                             | Encoded? |
  |---------------------|-------------------------------------------|:--------:|
  | Not present.        | `String`, `InputStream`, `File` or `ISeq` |    No.   |
  | Not present.        | Anything else.                            |   Yes.   |
  | application/transit | Anything.                                 |   Yes.   |
  | Other content type  | Anything.                                 |    No.   |


  #### Aleph Support

  If you have [Aleph](http://aleph.io/) in your classpath deferred
  responses will be handled properly using `manifold.deferred/chain`.
  "
  ([handler] (wrap-transit handler nil))
  ([handler options]
   (let [req-opts (select-keys options [:reader])
         res-opts (select-keys options [:writer])]
     (fn [request]
       (-> request
           (-decode-request req-opts)
           (handler)
           (chain #(-encode-response % res-opts)))))))


(defn ^:no-doc -decode-request [request options]
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


(defn ^:no-doc -encode-response [response options]
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
