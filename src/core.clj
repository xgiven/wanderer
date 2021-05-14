(ns wanderer.core
  (:require iota
            [instaparse.core :as insta]))

(def process-segm
  [file-vec] ;; -> (fn-type [state start] [state-new end])
  (fn [state start] ;; -> [state-new end]
    (let [
           tr (transient state)
           state-new (persistent! TODO)
           end TODO]
      [state-new end])))
