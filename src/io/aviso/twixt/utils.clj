(ns io.aviso.twixt.utils
  "Some re-usable utilities.

  Many of these are useful when creating new compilers or translators for Twixt."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [schema.core :as s]
            [io.aviso.twixt.schemas :refer :all])
  (:import [java.io CharArrayWriter ByteArrayOutputStream File]
           [java.util.zip Adler32]
           [java.net URL URI]
           [java.util Date]))

(defn ^String as-string
  "Converts a source (compatible with clojure.java.io/IOFactory) into a String using the provided encoding.

  The source is typically a byte array, or a File.

  The default charset is UTF-8."
  ([source]
   (as-string source "UTF-8"))
  ([source charset]
   (with-open [reader (io/reader source :encoding charset)
               writer (CharArrayWriter. 1000)]
     (io/copy reader writer)
     (.toString writer))))

(defn as-bytes [^String string]
  "Converts a string to a byte array. The string should be UTF-8 encoded."
  (.getBytes string "UTF-8"))

(s/defn compute-checksum :- Checksum
  "Returns a hex string of the Adler32 checksum of the content."
  [^bytes content]
  (->
    (doto (Adler32.)
      (.update content))
    .getValue
    Long/toHexString))

(s/defn replace-asset-content :- Asset
  "Modifies an Asset new content.
  This updates the :size and :checksum properties as well."
  [asset :- Asset
   content-type :- ContentType
   ^bytes content-bytes]
  (assoc asset
    :content-type content-type
    :content content-bytes
    :size (alength content-bytes)
    :checksum (compute-checksum content-bytes)))

(s/defn extract-dependency :- Dependency
  "Extracts from the asset the keys needed to track dependencies (used by caching logic)."
  [asset :- Asset]
  (select-keys asset [:checksum :modified-at :asset-path]))

(s/defn add-asset-as-dependency :- DependencyMap
  "Adds the dependencies of the Asset to a dependency map."
  [dependencies :- DependencyMap
   asset :- Asset]
  (merge dependencies (:dependencies asset)))

(s/defn create-compiled-asset :- Asset
  "Used to transform an Asset after it has been compiled from one form to another.
  Dependencies is a map of resource path to source asset details, used to check cache validity.

  The source asset's dependencies are merged into any provided dependencies to form the :dependencies entry of the output Asset."
  [source-asset :- Asset
   content-type :- s/Str
   content :- s/Str
   dependencies :- (s/maybe DependencyMap)]
  (let [merged-dependencies (add-asset-as-dependency (or dependencies {}) source-asset)]
    (->
      source-asset
      (replace-asset-content content-type (as-bytes content))
      (assoc :compiled true :dependencies merged-dependencies))))

(s/defn add-attachment :- Asset
  "Adds an attachment to an asset."
  {:since "0.1.13"}
  [asset :- Asset
   name  :- AttachmentName
   content-type :- ContentType
   ^bytes content]
  (assoc-in asset [:attachments name] {:content-type content-type
                                       :content      content
                                       :size         (alength content)}))

(defn read-content
  "Reads the content of a provided source (compatible with `clojure.java.io/input-stream`) as a byte array

  The content is usually a URI or URL."
  [source]
  (assert source "Unable to read content from nil.")
  (with-open [bos (ByteArrayOutputStream.)
              in (io/input-stream source)]
    (io/copy in bos)
    (.toByteArray bos)))

(defn compute-relative-path
  [^String start ^String relative]
  ;; Convert the start path into a stack of just the folders
  (loop [path-terms (-> (.split start "/") reverse rest)
         terms (-> relative (.split "/"))]
    (let [[term & remaining] terms]
      (cond
        (empty? terms) (->> path-terms reverse (str/join "/"))
        (or (= term ".") (= term "")) (recur path-terms remaining)
        (= term "..") (if (empty? path-terms)
                        ;; You could rewrite this with reduce, but then generating this exception would be more difficult:
                        (throw (IllegalArgumentException. (format "Relative path `%s' for `%s' would go above root." relative start)))
                        (recur (rest path-terms) remaining))
        :else (recur (cons term path-terms) remaining)))))

(defn- url-to-file
  [^URL url]
  (-> url .toURI File.))

(defn- jar-to-file
  "For a URL that points to a file inside a jar, this returns the JAR file itself."
  [^URL url]
  (-> url
      .getPath
      (str/split #"!")
      first
      URI.
      File.))

;; Not the same as io/file!
(defn- ^File as-file
  "Locates a file from which a last modified date can be extracted."
  [^URL url]
  (cond
    (= "jar" (.getProtocol url)) (jar-to-file url)
    :else (url-to-file url)))

(s/defn modified-at :- Date
  "Extracts a last-modified Date"
  [url :- URL]
  (if-let [^long time-modified (some-> url as-file .lastModified)]
    (Date. time-modified)))

(defn nil-check
  {:no-doc true}
  [value message]
  (or
    value
    (throw (NullPointerException. message))))

(defn path->name
  "Converts a path to just the file name, the last term in the path."
  [^String path]
  (let [slashx (.lastIndexOf path "/")]
    (if (< slashx 0)
      path
      (.substring path (inc slashx)))))

