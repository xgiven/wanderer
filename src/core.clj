(ns wanderer.core
  (:require iota
            [clojure.core.reducers :as r]
            [instaparse.core :as insta]))

(declare proc-file proc-segm parse-instr mk-linker)

(defn proc-file
  "Process a file, using (#'wanderer.core/mk-linker),
   by recursively forking when a conditional is reached,
   and continuing in both cases independently. Note that
   this uses agents, so you have to remember to call
   #'clojure.core/shutdown-agents at the end of your
   program. "
  [filename]
  (let [
         file-vec (iota/vec filename)
         graph-agent (agent {})]
    (let [
           segm-proc (proc-segm file-vec graph-agent)]
      (loop [state {}]
        (let [
               [new-state end] (proc-segm state start)]
          (c...))))))

(defn proc-segm
  "Process a segment of code (up until a conditional) by
   updating the state internally (as a transient),
   updating the graph by sending linker functions to
   the agent that stores the current graph
   (with #'wanderer.core/mk-linker), and finally, returning
   the new state as well as where it left off. "
  [file-vec graph-agent] ;; -> (fn-type [state start] [state-new end])
  (fn [state start] ;; -> [state-new end]
    (let [
           state-tr (transient state)]
      (let [
             end-found (loop [pos start]
                         (if-let [
                                   parsed (parse-instr (nth file-vec pos nil))] ;; EXTERN #'wanderer.core/parse-instr
                           (if ((comp :conditional :flow) parsed) pos ;; Only process unconditional instructions
                             (do
                               (run! (partial apply assoc! state-tr) ;; Update the current state
                                     (map :mut parsed))
                               (send graph-agent ;; Build the graph
                                     (apply mk-linker ;; EXTERN #'wanderer.core/mk-linker
                                            ((juxt :sources :target) parsed))) ;; [sources target]
                               (recur
                                 (or (:flow parsed) (inc pos)))))
                           nil))] ;; Yield nil if the file has been exhausted
        [(persistent! state-tr) end-found]))))

(defn mk-linker
  "Generate a linker function that adds the linkages
   represented by a new parsed instruction"
  [sources target]
  (apply comp ;; Compose all transformations
         (map (fn [source] #(assoc % source target))
              sources))) ;; From sources
