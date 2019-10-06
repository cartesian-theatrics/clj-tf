(ns clj-tf.tf-tree
  (:require
   [clj-tf.utils :as mat-utils :refer [quaternion->matrix translation->matrix]]
   [clojure.core.memoize]
   #?(:cljs [cljs.core.async.impl.timers :refer [skip-list SkipListNode]])
   [clojure.core.matrix :as mat])
  #?(:clj
     (:import [java.util.concurrent DelayQueue Delayed TimeUnit ConcurrentSkipListMap]
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
      [SkipListNode
       (get-key [this] (.-key this))
       (get-val [this] (.-val this))]))

(defn tf-msg->matrix [tf-msg]
  (let [tr (-> tf-msg :transform :translation)
        rotation (-> tf-msg :transform :rotation)]
    (mat/mmul (translation->matrix [(:x tr) (:y tr) (:z tr)])
              (quaternion->matrix rotation))))

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
  (lookup-transform [this t src-frame tgt-frame])
  (lookup-transform-chain [this t src-frame tgt-frame])
  (put-transform! [this t src-frame tgt-frame tf]))

(deftype TransformTree [^:mutable head #?@(:clj [^ConcurrentSkipListMap skip-list] :default [skip-list])]
  ITFTree
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
                               :cljs (SkipListNode. t nxt)))
        (.put skip-list t nxt))
      (let [[_ tree] (.floorEntry skip-list t)
            nxt (assoc tree src-frame [tgt-frame tf])]
        (.put skip-list t nxt)))))

(defn tf-tree []
  (let [skiplist #?(:clj (ConcurrentSkipListMap.) :cljs (skip-list))]
    (TransformTree. #?(:clj (AbstractMap$SimpleImmutableEntry. 0 {})
                       :cljs (SkipListNode. 0 {}))
                    skiplist)))
