(ns io.aviso.twixt.coffee-script
  "Provides asset pipeline middleware to perform CoffeeScript to JavaScript compilation."
  (:import [org.mozilla.javascript ScriptableObject]
           [java.util Map])
  (:require [io.aviso.tracker :as t]
            [io.aviso.twixt
             [rhino :as rhino]
             [utils :as utils]]))

(defn- ^String extract-value [^Map object key]
  (str (.get object key)))

(defn- coffee-script-compiler [asset context]
  (let [name (:resource-path asset)]
    (t/timer
      #(format "Compiled `%s' to JavaScript in %.2f ms" name %)
      (t/track
        #(format "Compiling `%s' to JavaScript" name)
        (let [^Map result
              (rhino/invoke-javascript ["META-INF/twixt/coffee-script.js" "META-INF/twixt/invoke-coffeescript.js"]
                                       "compileCoffeeScriptSource"
                                       (-> asset :content utils/as-string)
                                       name)]

          ;; The script returns an object with key "exception" or key "output":
          (when (.containsKey result "exception")
            (throw (RuntimeException. (extract-value result "exception"))))

          (utils/create-compiled-asset asset "text/javascript" (extract-value result "output")))))))

(defn register-coffee-script
  "Updates the Twixt options with support for compiling CoffeeScript into JavaScript."
  [options]
  (-> options
      (assoc-in [:content-types "coffee"] "text/coffeescript")
      (assoc-in [:content-transformers "text/coffeescript"] coffee-script-compiler)))