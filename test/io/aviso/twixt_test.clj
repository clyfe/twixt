(ns io.aviso.twixt-test
  (:use clojure.test
        io.aviso.twixt
        io.aviso.twixt.streamable
        clojure.tools.logging)
  (:require [clojure.java.io :as io]))

(defn next-handler [req] :next-handler)

(defn read-body [response]
  (->
    response
    :body
    read-content
    String.
    .trim))

(defn read-asset-content [asset]
  (->
    asset
    :content
    read-content
    String.
    .trim))

(defn read-resource-content [path]
  (->
    (str "META-INF/" path)
    io/resource
    read-content
    String.
    .trim))

(def cache-folder (format "%s/%x" (System/getProperty "java.io.tmpdir") (System/nanoTime)))

(def twixt (new-twixt {:development-mode true
                       :cache-folder     cache-folder}))
(def middleware (create-middleware twixt))
(def handler (-> next-handler middleware))

(deftest asset-pipeline
  (let [resolver (make-asset-resolver default-options)]
    (testing "asset not found"
      (is (nil? (resolver "does/not/exist.gif"))))

    (testing "asset found"
      (let [asset (resolver "coffeescript-source.coffee")]
        (is (not (nil? asset)))
        (is (-> asset :modified-at nil? not))
        (are [key value] (= (key asset) value)
                         :resource-path "META-INF/assets/coffeescript-source.coffee"
                         :content-type "text/coffeescript"
                         :size 30
                         :checksum "adbe0aaf")))))

#_ (deftest simple-content-access

  (deftest matching-file
    (let [response (handler {:uri "/assets/sample.js"})]
      (is (= (-> response :headers :content-type) "text/javascript"))
      (is (= (read-body response) (read-resource-content "assets/sample.js")))))

  (deftest non-matching-url

    (is (= (handler {:uri "/foo/bar.html"}) :next-handler)))

  (deftest folders-are-ignored

    (are [path] (is (= (handler {:uri path}) :next-handler))
                ;; exact match on the path prefix
                "/assets/"
                ;; any path that ends in a slash
                "/assets/sub/"))

  (deftest missing-file

    (is (= (handler {:uri "/assets/does-not-exist.html"}) :next-handler))))

#_ (deftest uri-generation

  (is (= (get-asset-uri twixt "sample.js") "/assets/sample.js"))
  (is (nil? (get-asset-uri twixt "does-not-exist.js"))))

(deftest coffeescript-compilation

  (let [pipeline (default-development-pipeline default-options)]

    (deftest succesful-compilation

      (let [asset (pipeline "coffeescript-source.coffee")]
        (is (= (-> asset :content-type) "text/javascript"))
        (is (= (-> asset :checksum) "5f181fb6"))
        (is (= (read-asset-content asset) (-> "assets/compiled-coffeescript-source.js" read-resource-content))))

      #_ (deftest compiled-results-are-cached
        (let [v1 (get-streamable twixt "coffeescript-source.coffee")
              v2 (get-streamable twixt "coffeescript-source.coffee")]

          ;; Clojure doesn't have a good way of checking that two references are to the same object (e.g., Groovy's is()
          ;; method). Instead, we compare the :content keys, which are a byte array, which should compare based on identity
          ;; rather than content.
          (is (= (:content v1) (:content v2)))))

      (deftest failed-compilation

        (is (thrown-with-msg? Exception
                              #"META-INF/assets/invalid-coffeescript.coffee:6:1: error: unexpected INDENT"
                              (pipeline "invalid-coffeescript.coffee")))))))

#_ (deftest jade-compilation

  (let [response (handler {:uri "/assets/jade-source.jade"})]
    (is (= (-> response :headers (get "Content-Type")) "text/html"))
    (is (= (-> response read-body) (-> "assets/compiled-jade-source.html" read-resource-content)))))

#_ (deftest relative-paths

  (are [start relative expected] (= (compute-relative-path start relative) expected)

                                 "foo/bar.gif" "baz.png" "foo/baz.png"

                                 "foo/bar.gif" "./baz.png" "foo/baz.png"

                                 "foo/bar.gif" "../zip.zap" "zip.zap"

                                 "foo/bar/gif" "../frozz/pugh.pdf" "foo/frozz/pugh.pdf")

  (is (thrown? IllegalArgumentException (compute-relative-path "foo/bar.png" "../../too-high.pdf"))))

#_ (deftest relative-streamables

  (let [f1 (get-streamable twixt "f1.txt")
        f2 (relative f1 "sub/f2.txt")
        f3 (relative f2 "f3.txt")
        missing (relative f3 "../does-not-exist.txt")]
    (are [streamable expected-content] (= (-> streamable as-string .trim) expected-content)
                                       f1 "file 1"
                                       f2 "file 2"
                                       f3 "file 3")

    (is (nil? missing))))

#_ (deftest less-compilation

  (let [streamable (get-streamable twixt "sample.less")
        expected (read-resource-content "assets/compiled-sample.css")]

    (is (= (content-type streamable) "text/css"))
    (is (= (-> streamable as-string .trim) expected)))


  (deftest compilation-failure

    (is (thrown-with-msg? Exception
                          #"META-INF/assets/invalid-less.less:3:5: no viable alternative at input 'p'"
                          (get-streamable twixt "invalid-less.less")
                          ))))