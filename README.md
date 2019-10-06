# Clojure TF

Simple ROS-style Transform Tree for Clojure(Script). It is designed to support the to efficient lookup the 
homogeneous matrix transform from a source frame to a target frame at a given time. Note that when performing
a lookup, the source frame must be a descendant of the target frame (i.e. arbitrary tf graph traversal is 
not supported).

It also includes utility functions for converting quaternions, eurler angles, translations, and standard ROS tf
messages to 4x4 matrices. See [clj-rosbag](https://github.com/cartesian-theatrics/clj-rosbag) for a library to
read ROS messages.

# Example

```clojure
(def tree (tf/tf-tree))

;; Add a transform at time t=10 from child frame "planar_lidar" to parent frame "base_link".
(tf/put-transform! tree 10 "planar_lidar" "base_link" (mat/identity-matrix 4))
(tf/put-transform! tree 13 "planar_lidar" "base_link" (-> (mat/identity-matrix 4)
                                                          (mat/mmul (tf-utils/translation->matrix [1 2 3]))))

;; Lookup the nearest transform at time t=15 from "planar_lidar" to "base_link".
;; Note, unlike ROS tf, this library won't do any sophisticated bounds checking,
;; nor will it extrapolate with any kind of forward prediction model.
(mat/to-nested-vectors (tf/lookup-transform tree 15 "planar_lidar" "base_link"))
;; [[1 0 0 1] [0 1 0 2] [0 0 1 3] [0 0 0 1]]

(mat/to-nested-vectors (tf/lookup-transform tree 11 "planar_lidar" "base_link"))
;; [[1 0 0 0] [0 1 0 0] [0 0 1 0] [0 0 0 1]]
```
