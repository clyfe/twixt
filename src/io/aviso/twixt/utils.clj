(ns io.aviso.twixt.utils
  "Some re-usable utilities. This namespace should be considered unsupported.")

(defn transform-values 
  "Transforms a map by passing each value through a provided function."
  [m f]
  (into {} (map (fn [[k v]] [k (f v)]) m)))

(declare merge-maps-recursively)

(defn- merge-values [l r]
  (cond
    ;; We know how to merge two maps together:
    (and (map? l) (map? r)) (merge-maps-recursively l r)
    ;; and how to merge two seqs  together
    (and (seq? l) (seq? r)) (concat l r)
    ;; In any other case the right (later) value replaces the left (earlier) value
    :else r))

(defn merge-maps-recursively 
  "Merges any number of maps together, recursively. When merging values:
  - two maps are merged, recursively
  - two seqs are concatinated
  - otherwise, the 'right' value overwrites the 'left' value"
  [& maps]
  (apply merge-with merge-values maps))