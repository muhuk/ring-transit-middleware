;; Copyright © 2015 Atamert Ölçgen
;;
;; Distributed under the The MIT License
;; http://opensource.org/licenses/MIT

(ns ring.middleware.test-transit
  (:require [clojure.test :refer :all]
            [cognitect.transit :as t]
            [ring.middleware.transit :refer :all]
            [ring.util.response :refer [content-type
                                        response]])
  (:import [java.io ByteArrayInputStream ByteArrayOutputStream]))


(deftest test-decode-request
  (testing "a transit request gets decoded."
    (let [body-str (encode {:foo 42
                            :bar "»» µ ««"})
          body (-> body-str
                   (.getBytes "UTF-8")
                   (ByteArrayInputStream.))]
      (is (= {:headers {"content-type" "application/transit+json"}
              :body body-str
              :transit-params {:foo 42
                               :bar "»» µ ««"}
              :params {:foo 42
                       :bar "»» µ ««"}}
             (-decode-request {:headers {"content-type" "application/transit+json"}
                               :body body}
                              nil)))))
  (testing "charset is taken into account."
    (let [body-str (encode {:foo 42
                            :bar "»» µ ««"})
          body (-> body-str
                   (.getBytes "UTF-16")
                   (ByteArrayInputStream.))]
      (is (= {:headers {"content-type" "application/transit+json; charset=utf-16"}
              :body body-str
              :transit-params {:foo 42
                               :bar "»» µ ««"}
              :params {:foo 42
                       :bar "»» µ ««"}}
             (-decode-request {:headers {"content-type" "application/transit+json; charset=utf-16"}
                               :body body}
                              nil)))))
  (testing "only maps are merged with :params."
    (let [body-str (encode ['x 'y])
          body (-> body-str
                   (.getBytes "UTF-8")
                   (ByteArrayInputStream.))]
      (is (= {:headers {"content-type" "application/transit+json"}
              :body body-str
              :transit-params ['x 'y]}
             (-decode-request {:headers {"content-type" "application/transit+json"}
                               :body body}
                              nil)))))
  (testing "a non-transit request passes unchanged."
    (let [request {:headers {"content-type" "text/plain"}}]
      (is (= request
             (-decode-request request nil))))))


(deftest test-encode-response
  (testing "collections sans ISeq, without content-type."
    (are [?body] (= (-> (encode ?body)
                        (response)
                        (content-type "application/transit+json; charset=utf-8"))
                    (-encode-response (response ?body) nil))
         ;; Collections that don't implement ISeq work.
         {:foo :bar}
         [1 2 3]
         #{:a :b :c}
         ;; So does atomic values.
         :kw
         'sym
         1
         true
         999999999999N
         0.0000000001M))
  (testing "strings, InputStream's, File's & ISeq's, without content-type."
    (are [?body] (let [r (response ?body)]
                   (= r
                      (-encode-response r nil)))
         "Foo"
         (ByteArrayInputStream. (byte-array 1))
         (java.io.File. "/non/existent/file")
         '(foo bar baz)))
  (testing "when content-type is transit encoding is forced."
    (is (= (-> "This is encoded."
               (encode)
               (response)
               (content-type "application/transit+json"))
           (-> "This is encoded."
               (response)
               (content-type "application/transit+json")
               (-encode-response nil))))
    (is (= (-> "This is encoded."
                 (encode)
                 (response)
                 (content-type "application/transit+msgpack"))
             (-> "This is encoded."
                 (response)
                 (content-type "application/transit+msgpack")
                 (-encode-response nil))))
    (is (= (-> (encode (list :a 'b "c"))
               (response)
               (content-type "application/transit+json"))
           (-> (list :a 'b "c")
               (response)
               (content-type "application/transit+json")
               (-encode-response nil))))
    (let [input-stream (java.io.ByteArrayInputStream. (byte-array 0))]
      (is (thrown-with-msg? RuntimeException
                            #"Not supported"
                            (-> input-stream
                                (response)
                                (content-type "application/transit+msgpack")
                                (-encode-response nil))))))
  (testing "when content-type is non-transit response is never encoded."
    (are [?body] (let [r (-> ?body
                             (response)
                             (content-type "definitely/not"))]
                   (= r
                      (-encode-response r nil)))
         {:foo :bar}
         [1 2 3]
         #{:a :b :c}
         :kw
         'sym
         1
         true
         999999999999N
         0.0000000001M)))
