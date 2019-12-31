(ns clj-tf.core
  (:require
   #?(:clj [clojure.core.memoize]
      :cljs [cljs.core.async.impl.timers :refer [skip-list]])
   [clojure.core.matrix :as mat])
  #?(:clj
     (:import [java.util.concurrent ConcurrentSkipListMap]
              [java.util AbstractMap$SimpleImmutableEntry])))

(defprotocol ISkipListEntry
  (get-key [this])
  (get-val [this]))

(extend-protocol ISkipListEntry
  #?@(:clj
      [AbstractMap$SimpleImmutableEntry
       (get-key [this] (.getKey this))
       (get-val [this] (.getValue this))]
      :cljs
      [cljs.core.MapEntry
       (get-key [this] (.-key this))
       (get-val [this] (.-val this))]))

(defn compute-transform [tree src-frame tgt-frame]
  (let [[parent-frame tf-mat] (get tree src-frame)]
    (when parent-frame
      (if (= parent-frame tgt-frame)
        tf-mat
        (when-let [tf (compute-transform tree parent-frame tgt-frame)]
          (mat/mmul tf tf-mat))))))

(defn compute-transform-seq [tree src-frame tgt-frame]
  (let [[parent-frame tf-mat] (get tree src-frame)]
    (when parent-frame
      (if (= parent-frame tgt-frame)
        [[src-frame tgt-frame tf-mat]]
        (when-let [tfs (compute-transform-seq tree parent-frame tgt-frame)]
          (conj tfs [src-frame tgt-frame (mat/mmul (peek (peek tfs)) tf-mat)]))))))

(defprotocol ITFTree
  (lookup-transform [this src-frame tgt-frame] [this t src-frame tgt-frame])
  (lookup-transform-chain [this t src-frame tgt-frame])
  (put-transform! [this t src-frame tgt-frame tf]))

(deftype TransformTree [^:mutable head #?@(:clj [^ConcurrentSkipListMap skip-list] :default [skip-list])]
  ITFTree
  (lookup-transform [this src-frame tgt-frame]
    (compute-transform (get-val (.-head this)) src-frame tgt-frame))
  (lookup-transform [this t src-frame tgt-frame]
    (when-let [entry (.floorEntry skip-list t)]
      (compute-transform (get-val entry)
                         src-frame
                         tgt-frame)))
  (lookup-transform-chain [this t src-frame tgt-frame]
    (when-let [entry (.floorEntry skip-list t)]
      (compute-transform-seq (get-val entry)
                             src-frame
                             tgt-frame)))
  (put-transform! [this t src-frame tgt-frame tf]
    (let [tree (get-val (.-head this))
          v (assoc tree src-frame [tgt-frame tf])]
      (set! (.-head this)
            #?(:clj (AbstractMap$SimpleImmutableEntry. t v)
               :cljs (MapEntry. t v nil)))
      (.put skip-list t v))
    this))

(defn tf-tree
  ([tfs]
   (let [skiplist #?(:clj (ConcurrentSkipListMap.) :cljs (skip-list))
         tree (reduce (fn [ret [src-frame tgt-frame tf]]
                        (assoc ret src-frame [tgt-frame tf]))
                      {}
                      tfs)]
     (TransformTree. #?(:clj (AbstractMap$SimpleImmutableEntry. (java.sql.Timestamp. 0) tree)
                        :cljs (MapEntry. 0 {} nil))
                     skiplist)))
  ([]
   (tf-tree [])))

(defn tf-tree? [x]
  (instance? x TransformTree))

(comment 
  (def tree (tf-tree))
  (require '[clj-tf.utils :as utils])

  (put-transform! tree (java.sql.Timestamp. 10) "a" "b" (mat/identity-matrix 4))
  (put-transform! tree (java.sql.Timestamp. 20) "a" "b" (mat/mmul
                                                         (mat/identity-matrix 4)
                                                         (utils/translation->matrix [1 2 3])))
  (put-transform! tree (java.sql.Timestamp. 30) "a" "b" (mat/mmul
                                                         (mat/identity-matrix 4)
                                                         (utils/translation->matrix [1 55 3])))

  (lookup-transform tree (java.sql.Timestamp. 25) "a" "b")

  (def entry (.floorEntry (.-skip-list tree) (java.sql.Timestamp. 27)))
  (.getValue (.getValue (.getValue entry)))


  (.getValue (.floorEntry (.-skip-list tree) (java.sql.Timestamp. 27)))


  )
