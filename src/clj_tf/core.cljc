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

(def compute-transform
  (#?(:clj clojure.core.memoize/lru :cljs identity)
   (fn [tf-tree src-frame tgt-frame]
     (let [[parent-frame tf-mat] (tf-tree src-frame)]
       (when parent-frame
         (if (= parent-frame tgt-frame)
           tf-mat
           (when-let [tf (compute-transform tf-tree parent-frame tgt-frame)]
             (mat/mmul tf tf-mat))))))))

(def compute-transform-seq
  (#?(:clj clojure.core.memoize/lru :cljs identity)
   (fn [tf-tree src-frame tgt-frame]
     (let [[parent-frame tf-mat] (tf-tree src-frame)]
       (when parent-frame
         (if (= parent-frame tgt-frame)
           [[src-frame tgt-frame tf-mat]]
           (when-let [tfs (compute-transform-seq tf-tree parent-frame tgt-frame)]
             (conj tfs  [src-frame tgt-frame (mat/mmul (peek (peek tfs)) tf-mat)]))))))))

(defprotocol ITFTree
  (lookup-transform [this src-frame tgt-frame] [this t src-frame tgt-frame])
  (lookup-transform-chain [this t src-frame tgt-frame])
  (put-transform! [this t src-frame tgt-frame tf]))

(deftype TransformTree [^:mutable head #?@(:clj [^ConcurrentSkipListMap skip-list] :default [skip-list])]
  ITFTree
  (lookup-transform [this src-frame tgt-frame]
    (compute-transform (get-val (.-head this)) src-frame tgt-frame))
  (lookup-transform [this t src-frame tgt-frame]
    (compute-transform (get-val (.floorEntry skip-list t))
                       src-frame
                       tgt-frame))
  (lookup-transform-chain [this t src-frame tgt-frame]
    (compute-transform-seq (get-val (.floorEntry skip-list t))
                           src-frame
                           tgt-frame))
  (put-transform! [this t src-frame tgt-frame tf]
    (if (> t (get-key (.-head this)))
      (let [nxt (assoc (get-val (.-head this)) src-frame [tgt-frame tf])]
        (set! (.-head this) #?(:clj (AbstractMap$SimpleImmutableEntry. t nxt)
                               :cljs (MapEntry. t nxt nil)))
        (.put skip-list t nxt))
      (let [tree (get-val (.floorEntry skip-list t))
            nxt (assoc tree src-frame [tgt-frame tf])]
        (.put skip-list t nxt)))
    this))

(defn tf-tree
  ([tfs]
   (let [skiplist #?(:clj (ConcurrentSkipListMap.) :cljs (skip-list))
         tree (reduce (fn [ret [src-frame tgt-frame tf]]
                        (assoc ret src-frame [tgt-frame tf]))
                      {}
                      tfs)]
     (TransformTree. #?(:clj (AbstractMap$SimpleImmutableEntry. 0 tree)
                        :cljs (MapEntry. 0 {} nil))
                     skiplist)))
  ([]
   (tf-tree [])))
