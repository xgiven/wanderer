(ns wanderer.core
  (:require iota
            [instaparse.core :as insta]))

(def process-segm
  "Process a segment of code (up until a conditional) by
   updating the state internally (as a transient) and
   updating the graph by sending linker functions to
   the agent that stores the current graph (with mk-linker)"
  [file-vec graph-agent] ;; -> (fn-type [state start] [state-new end])
  (fn [state start] ;; -> [state-new end]
    (let [
           state-tr (transient state)
           state-new (do
                       (persistent! state-tr))
           end TODO]
      [state-new end])))
