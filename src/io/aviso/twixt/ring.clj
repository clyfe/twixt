(ns io.aviso.twixt.ring
  "Support needed to use Twixt inside a Ring handler pipeline."
  (:import [java.util Calendar TimeZone])
  (:require
    [clojure.java.io :as io]
    [io.aviso.tracker :as t]
    [io.aviso.twixt [asset :as asset]]
    [ring.util.response :as r]))

(def ^:private far-future
  (-> (doto (Calendar/getInstance (TimeZone/getTimeZone "UTC"))
        (.add Calendar/YEAR 10))
      .getTime))

(defn- asset->ring-response
  [asset]
  (cond->
    ;; First the standard stuff ...
    (-> asset
        :content
        io/input-stream
        r/response
        (r/header "Content-Length" (:size asset))
        ;; Because any change to the content will create a new checksum and a new URL, a change to the content
        ;; is really an entirely new resource. The current resource is therefore immutable and can have a far-future expires
        ;; header.
        (r/header "Expires" far-future)
        (r/content-type (:content-type asset)))
    ;; The optional extras ...
    (:compressed asset) (r/header "Content-Encoding" "gzip")))


(defn- match? [^String path-prefix ^String path]
  (and
    (.startsWith path path-prefix)
    (not (or (= path path-prefix)
             (.endsWith path "/")))))

(defn- parse-path
  "Parses the complete request path into a checksum, compressed-flag, and asset path."
  [^String path-prefix ^String path]
  (let [suffix (.substring path (.length path-prefix))
        slashx (.indexOf suffix "/")
        full-checksum (.substring suffix 0 slashx)
        compressed? (.startsWith full-checksum "z")
        checksum (if compressed? (.substring full-checksum 1) full-checksum)
        asset-path (.substring suffix (inc slashx))]
    [checksum
     compressed?
     asset-path]))

(defn- asset->redirect-response
  [status path-prefix asset]
  {:status  status
   :headers {"Location" (asset/asset->request-path path-prefix asset)}
   :body    ""})

(def ^:private asset->301-response (partial asset->redirect-response 301))

(defn- create-asset-response
  [path-prefix requested-checksum asset]
  (cond
    (nil? asset) nil
    (= requested-checksum (:checksum asset)) (asset->ring-response asset)
    :else (asset->301-response path-prefix asset)))

(defn twixt-handler
  "A Ring request handler that identifies requests targetted for Twixt assets.  Returns a Ring response map
  if the request is for an existing asset, otherwise returns nil.

  Asset URLs always include the intended asset's checksum; if the actual asset checksum does not match, then
  a 301 (moved permanently) response is sent with the correct asset URL."
  [request]
  (let [path-prefix (-> request :twixt :path-prefix)
        path (:uri request)]
    (when (match? path-prefix path)
      (t/track
        #(format "Handling asset request `%s'" path)
        (let [[requested-checksum compressed? asset-path] (parse-path path-prefix path)
              context (:twixt request)
              ;; When actually servicing an asset request, we have to trust the data in the URL
              ;; that determines whether to server the normal or gzip'ed resource.
              context' (assoc context :gzip-enabled compressed?)
              asset-pipeline (:asset-pipeline context)]
          (create-asset-response path-prefix
                                 requested-checksum
                                 (asset-pipeline asset-path context')))))))

(defn- handle-asset-redirect
  [uri context]
  ;; This may be too specific, may need to find a better way to differentiate between a "folder" and a file.
  (if (not (= uri "/"))
    (let [{:keys [asset-pipeline path-prefix]} context
          asset-path (.substring uri 1)]
      (if-let [asset (asset-pipeline asset-path context)]
        (asset->redirect-response 302 path-prefix asset)))))

(defn wrap-with-asset-redirector
  "In some cases, it is not possible for the client to know what the full asset URI will be, such as when the
  URL is composed on the client (in which case, the asset checksum will not be known). The redirector accepts
  any request path that maps to an asset and returns a redirect to the asset's true URL. Non-matching
  requests are passed through to the provided handler.

  For a file under `META-INF/assets`, such as `META-INF/assets/myapp/icon.png`, the redirector will match
  the URI `/myapp/icon.png` and send a redirect to `/assets/123abc/myapp/icon.png`.

  This middleware is not applied by default."
  [handler]
  (fn [{uri     :uri
        context :twixt
        :as     request}]
    (or
      (handle-asset-redirect uri context)
      (handler request))))

(defn wrap-with-twixt
  "Invokes the twixt-handler and delegates to the provided Ring handler if twixt-handler returns nil.

  This assumes that the resulting handler will then be wrapped with the twixt setup.

  In most cases, you will want to use the wrap-with-twixt function in the startup namespace."
  [handler]
  (fn [request]
    (or (twixt-handler request)
        (handler request))))

(defn wrap-with-twixt-setup
  "Wraps a Ring handler with another Ring handler that provides the :twixt key in the request object.

  The :twixt key is the default asset pipeline context, which is needed by get-asset-uri in order to resolve asset paths
  to an actual asset. It also contains the keys :asset-pipeline (the pipeline used to resolve assets) and
  :stack-frame-filter (which is used by the HTML exception report).

  This provides the information needed by the actual Twixt handler, as well as anything else downstream that needs to
  generate Twixt asset URIs."
  [handler twixt-options asset-pipeline]
  (let [twixt (-> twixt-options
                  ;; Pass down only what is needed to generate asset URIs, or to produce the HTML exception report.
                  (select-keys [:path-prefix :stack-frame-filter])
                  (assoc :asset-pipeline asset-pipeline))]
    (fn [request]
      (handler (assoc request :twixt twixt)))))