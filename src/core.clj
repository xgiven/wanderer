(ns wanderer.core
  (:require iota
            [instaparse.core :as insta]))

(def proc-segm
  "Process a segment of code (up until a conditional) by
   updating the state internally (as a transient),
   updating the graph by sending linker functions to
   the agent that stores the current graph (with mk-linker),
   and finally, returning the new state as well as where it
   left off. "
  [file-vec graph-agent] ;; -> (fn-type [state start] [state-new end])
  (fn [state start] ;; -> [state-new end]
    (let [
           state-tr (transient state)]
      (let [
             end-found (loop [pos start]
                         (let [parsed (parse-line file-vec pos)]
                           (if (parsed :cond?) pos
                             (do
                               (merge! state-tr
                                       (proc-line (nth file-vec pos)
                                                  graph-agent))
                               (recur
                                 (or (:target parsed) (inc pos)))))))]
        [(persistent! state-tr) end-found]))))
