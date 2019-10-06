# Clojure TF

Simple ROS-style Transform Tree for Clojure(Script). It is designed to support the to efficient lookup the 
homogeneous matrix transform from a source frame to a target frame at a given time. Note that when performing
a lookup, the source frame must be a descendant of the target frame (i.e. arbitrary tf graph traversal is 
not supported).

It also includes utility functions for converting quaternions, eurler angles, and translations to 4x4 matrices.

# Example

```clojure
(def tree (tf/tf-tree))

;; 
(tf/put-transform! tree 10 "planar" "slanted" (mat/identity-matrix 4))
(tf/put-transform! tree 13 "planar" "slanted" (-> (mat/identity-matrix 4)
                                              (mat/mmul (tf-utils/translation->matrix [1 2 3]))))

(mat/to-nested-vectors (tf/lookup-transform tree 15 "planar" "slanted"))
;; [[1 0 0 1] [0 1 0 2] [0 0 1 3] [0 0 0 1]]

(mat/to-nested-vectors (tf/lookup-transform tree 11 "planar" "slanted"))
;; [[1 0 0 0] [0 1 0 0] [0 0 1 0] [0 0 0 1]]
```
