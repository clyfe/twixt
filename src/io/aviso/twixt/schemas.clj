(ns io.aviso.twixt.schemas
  "Defines schemas for the main types and functions."
  {:added "0.1.17"}
  (:require [schema.core :as s]))

(def Content
  "Defines the content for an Asset; this is any value compatible with clojure.java.io."
  s/Any)

(def AssetPath
  "Path of the asset under the root folder (which is typically `/META-INF/assets/`)."
  s/Str)

(def ResourcePath
  "Full path of the underlying resource (on the classpath)."
  s/Str)

(def ContentType
  "The MIME type of the content, as determined form the path's extension."
  s/Str)

(def Size
  "Size of the content, in bytes."
  s/Num)

(def Checksum
  "Computed Adler32 checksum of the content, as a string of hex characters."
  s/Str)

(def ModifiedAt
  "Instant at which an asset was last modified; this may not be accurate for assets
  stored within a JAR file, but will be adequate for determining if a file's content
  has changed."
  s/Inst)

(def AttachmentName
  "A unique logical name that represents an attachment to an asset.
  Attachments are a limited form of an Asset typically generated as a side-effect
  of creating/compiling the asset; the canonical example is a JavaScript source map."
  s/Str)

(s/defschema Asset
  "A server-side resource that may be exposed to a client (such as a web browser).
  An Asset may be transformed from raw content, for example to transpile a language
  to JavaScript. Other transformations include compression."
  {:asset-path                             AssetPath
   :resource-path                          ResourcePath
   :content-type                           ContentType
   :size                                   Size
   :checksum                               Checksum
   :modified-at                            ModifiedAt
   (s/optional-key :compiled)              s/Bool
   (s/optional-key :aggregate-asset-paths) [AssetPath]
   (s/optional-key :dependencies)          {ResourcePath {:asset-path  AssetPath
                                                          :checksum    Checksum
                                                          :modified-at ModifiedAt}}
   (s/optional-key :attachments)           {AttachmentName {:content      Content
                                                            :size         Size
                                                            :content-type ContentType}}})

(s/defschema TwixtContext
  "Defines the minimal values provided in the Twixt context (the :twixt key of the Ring
  request map)."
  {(s/optional-key :asset-pipeline) s/Any                   ; should be AssetHandler
   :path-prefix                     s/Str
   s/Any                            s/Any})

(s/defschema AssetHandler
  "An asset handler is passed as asset path and the Twixt context and, maybe, returns
  an Asset."
  (s/=> (s/maybe Asset) AssetPath TwixtContext))



(def AssetURI
  "A URI that allows an external client to access the client content. AssetURIs typically
  include the checksum in the URI path, forming an immutable web resource."
  s/Str)