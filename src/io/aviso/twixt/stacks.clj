(ns io.aviso.twixt.stacks
  "Stacks are a way of combining several related files together to form a single aggregate virtual file.
  In development, the individual files are used, but in production they are aggregated together so that
  the entire stack can be obtained in a single request."
  {:since "0.1.13"}
  (:require [io.aviso.tracker :as t]
            [io.aviso.twixt.utils :as utils]
            [clojure.java.io :as io]
            [clojure.edn :as edn])
  (:import (java.io PushbackReader ByteArrayOutputStream)))

(def stack-mime-type
  "Defines a MIME type for a Twixt stack, a kind of aggregate resource that combines
  multiple other resources."
  "application/vnd.twixt-stack+edn")

(defn- read-stack
  [asset]
  (-> asset :content io/reader PushbackReader. edn/read))

(defn- update-aggregate-asset-paths
  [asset-paths component-asset]
  (if-let [aggregate-paths (-> component-asset :aggregate-asset-paths seq)]
    (concat asset-paths aggregate-paths)
    (conj asset-paths (:asset-path component-asset))))

(defn- include-component
  [{:keys [asset-pipeline] :as context} {:keys [asset-path] :as asset} component-path content-stream]
  ;; This may only be correct for relative, not absolute, paths:
  (let [complete-path (utils/compute-relative-path asset-path component-path)
        component-asset (asset-pipeline complete-path context)]

    (if-not (some? component-asset)
      (throw (ex-info (format "Could not locate resource `%s' (a component of `%s')."
                              complete-path
                              asset-path)
                      context)))

    (io/copy (:content component-asset) content-stream)

    ;; Now add the component asset as a dependency by itself, or
    ;; add the component asset's dependencies

    (-> asset
        (update-in [:dependencies] utils/add-asset-as-dependency component-asset)
        (update-in [:aggregate-asset-paths] (fnil update-aggregate-asset-paths []) component-asset))))

(defn- aggregate-stack
  [{:keys [resource-path] :as asset} context]
  (t/track
    #(format "Reading stack `%s' and aggregating contents." resource-path)
    ;; Make sure that the aggregated asset is not compressed
    (let [context' (assoc context :for-aggregation true)
          stack (read-stack asset)]

      (assert (-> stack :content-type some?))
      (assert (-> stack :components empty? not))

      (with-open [content-stream (ByteArrayOutputStream. 5000)]
        (-> (reduce (fn [asset component-path] (include-component context' asset component-path content-stream)) asset (:components stack))
            (assoc-in [:dependencies resource-path] (utils/extract-dependency asset))
            (utils/replace-asset-content (:content-type stack) (.toByteArray content-stream))
            (assoc :compiled true))))))

(defn register-stacks
  "Updates the Twixt options with support for stacks."
  [options]
  (-> options
      (assoc-in [:content-types "stack"] stack-mime-type)
      (assoc-in [:content-transformers stack-mime-type] aggregate-stack)))