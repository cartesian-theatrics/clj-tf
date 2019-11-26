(ns clj-tf.utils
  (:require
   [clojure.core.matrix :as mat]
   [clojure.core.matrix.selection :as s]))

(defn matrix44
  [data] (-> (mat/matrix data)
             (mat/reshape [4 4])))

(defn translation->matrix [[x y z]]
  (matrix44
   [1 0 0 x
    0 1 0 y
    0 0 1 z
    0 0 0 1]))

(defn quaternion->matrix
  ([quaternion]
   (let [q (mat/matrix [(:x quaternion) (:y quaternion) (:z quaternion) (:w quaternion)])
         nq (mat/dot q q)
         q (mat/e* q (Math/sqrt (/ 2.0 nq)))
         q (mat/outer-product q q)
         s s/sel]
     (matrix44
      [(- 1.0 (s q 1 1) (s q 2 2)) (- (s q 0 1) (s q 2 3))     (+ (s q 0 2) (s q 1 3))     0
       (+ (s q 0 1) (s q 2 3))     (- 1.0 (s q 0 0) (s q 2 2)) (- (s q 1 2) (s q 0 3))     0
       (- (s q 0 2) (s q 1 3))     (+ (s q 1 2) (s q 0 3))     (- 1.0 (s q 0 0) (s q 1 1)) 0
       0.0                         0.0                         0.0                         1.0]))))

(def axes->tuple {:sxyz [0 0 0 0] :sxyx [0 0 1 0] :sxzy [0 1 0 0]
                  :sxzx [0 1 1 0] :syzx [1 0 0 0] :syzy [1 0 1 0]
                  :syxz [1 1 0 0] :syxy [1 1 1 0] :szxy [2 0 0 0]
                  :szxz [2 0 1 0] :szyx [2 1 0 0] :szyz [2 1 1 0]
                  :rzyx [0 0 0 1] :rxyx [0 0 1 1] :ryzx [0 1 0 1]
                  :rxzx [0 1 1 1] :rxzy [1 0 0 1] :ryzy [1 0 1 1]
                  :rzxy [1 1 0 1] :ryxy [1 1 1 1] :ryxz [2 0 0 1]
                  :rzxz [2 0 1 1] :rxyz [2 1 0 1] :rzyz [2 1 1 1]})

(def next-axis [1 2 0 1])

(defn euler->matrix
  ([angles]
   (euler->matrix angles :sxyz))
  ([[ak aj ai] axes]
   (let [[first-axis parity repetition frame] (axes->tuple axes)
         i first-axis
         j (next-axis (+ i parity))
         k (next-axis (let [idx (inc (- i parity))]
                        (if (not (neg? idx))
                           idx
                           (+ (count next-axis) idx))))
         [ai aj ak] (if (pos? frame) [ak aj ai] [ai aj ak])
         [ai aj ak] (if (pos? parity)
                      [(- ai) (- aj) (- ak)]
                      [ai aj ak])
         si (Math/sin ai) sj (Math/sin aj) sk (Math/sin ak)
         ci (Math/cos ai) cj (Math/cos aj) ck (Math/cos ak)
         cc (* ci ck) cs (* ci sk)
         sc (* si ck) ss (* si sk)
         m (mat/mutable (mat/identity-matrix 4))
         s! s/set-sel!]
     (if (pos? repetition)
       (-> m
           (s! i i cj)
           (s! i j (* sj si))
           (s! i k (* sj ci))
           (s! j i (* sj sk))
           (s! j j (+ (* (- cj) ss) cc))
           (s! j k (- (* (- cj) cs) sc))
           (s! k i (- (* sj ck)))
           (s! k j (+ (* cj sc) cs))
           (s! k k (- (* cj cc) ss)))
       (-> m
           (s! i i (* cj ck))
           (s! i j (- (* sj sc) cs))
           (s! i k (+ (* sj cc) ss))
           (s! j i (* cj sk))
           (s! j j (+ (* sj ss) cc))
           (s! j k (- (* sj cs) sc))
           (s! k i (- sj))
           (s! k j (* cj si))
           (s! k k (* cj ci)))))))

(defn rotate [matrix axis radians]
  (case axis
    :x (mat/mmul matrix (euler->matrix [0 0 radians]))
    :y (mat/mmul matrix (euler->matrix [0 radians 0]))
    :z (mat/mmul matrix (euler->matrix [radians 0 0]))))

(defn tf-msg->matrix [tf-msg]
  (let [tr (-> tf-msg :transform :translation)
        rotation (-> tf-msg :transform :rotation)]
    (mat/mmul (translation->matrix [(:x tr) (:y tr) (:z tr)])
              (quaternion->matrix rotation))))
