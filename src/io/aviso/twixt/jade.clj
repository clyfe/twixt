(ns io.aviso.twixt.jade
  "Provides asset pipeline middleware for compiling Jade templates to HTML using jade4j."
  (:import [de.neuland.jade4j Jade4J]
           [de.neuland.jade4j.exceptions JadeException])
  (:require [io.aviso.twixt
             [tracker :as tracker]
             [utils :as utils]]
            [clojure.java.io :as io]))

(defn- jade-compiler [pretty-print asset context]
  (let [name (:resource-path asset)]
    (tracker/log-time
      #(format "Compiled `%s' to HTML in %.2f ms" name %)
      (tracker/trace
        #(format "Compiling `%s' from Jade to HTML" name)
        (try
          (with-open [reader (-> asset :content io/reader)]
            (->>
              (Jade4J/render reader name {} pretty-print)
              (utils/create-compiled-asset asset "text/html")))
          (catch JadeException e
            (throw (RuntimeException.
                     (format "Jade Compilation exception on line %d: %s"
                             (.getLineNumber e)
                             (or (.getMessage e) (-> e .getClass .getName)))
                     e))))))))

(defn wrap-with-jade-compilation
  [handler pretty-print]
  (utils/content-type-matcher handler "text/jade" (partial jade-compiler pretty-print)))

