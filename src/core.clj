(ns wanderer.core
  (:require iota
            [instaparse.core :as insta]))

(def proc-segm
  "Process a segment of code (up until a conditional) by
   updating the state internally (as a transient),
   updating the graph by sending linker functions to
   the agent that stores the current graph
   (with 'wanderer.core/mk-linker), and finally, returning
   the new state as well as where it left off. "
  [file-vec graph-agent] ;; -> (fn-type [state start] [state-new end])
  (fn [state start] ;; -> [state-new end]
    (let [
           state-tr (transient state)]
      (let [
             end-found (loop [pos start]
                         (let [parsed (parse-line (nth file-vec pos))] ;; extern 'wanderer.core/parse-line
                           (if (parsed :flow/condition) pos
                             (do
                               (run! (partial apply assoc! state-tr)
                                     (map :mut parsed))
                               (send graph-agent
                                     (mk-linker ;; extern 'wanderer.core/mk-linker
                                       ((juxt :op/sources :op/target) parsed)))
                               (recur
                                 (or (:flow/target parsed) (inc pos)))))))]
        [(persistent! state-tr) end-found]))))
