(ns wanderer.core
  (:require iota
            [instaparse.core :as insta]
            [clojure.core.reducers :as r]
            [clojure.core.async :as async]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]))

;; functions (provided)
(declare proc-file proc-files)
;;       TODO      TODO

;; functions (provided, but for internal use)
(declare proc-asm proc-segm parse-instr mk-linker)
;;       DONE     TODO      TODO        TODO

;; constants
(declare num-opcodes
         max-cond-depth trampoline-channel-size)

;; constant definitions (specific to system context)
(def num-opcodes 900)

;; constant definitions (operational parameters)
(def max-cond-depth 10)
(def trampoline-channel-size 100)

;; basic specs

(s/def ::opcode (s/and int?
                       (complement neg?)
                       #(< % num-opcodes)))

(s/def ::graph (s/coll-of
                (s/coll-of ::opcode
                           :kind set?
                           :max-count num-opcodes)
                :kind vector?
                :count num-opcodes))

(s/def ::line (s/and int?
                     (complement neg?)))

(s/def ::op-ref (s/tuple ::opcode
                         (s/keys :req [::line])))
(s/def ::mem-ref (s/tuple string?
                          (s/keys :req [::line])))

(s/def ::stack-ops (s/coll-of ::op-ref
                              :kind vector?))
(s/def ::call-stack (s/coll-of ::line
                               :kind vector?))
(s/def ::cmp-refs (s/tuple ::mem-ref ::mem-ref))
(s/def ::op-sources (s/map-of ::mem-ref ::op-ref))
(s/def ::flow-sources (s/coll-of ::op-ref
                                 :kind set?))
(s/def ::mov-sources (s/map-of ::mem-ref
                               ::mem-ref))
(s/def ::cond-depth (s/and int?
                           (complement neg?)))

(s/def ::state
  (s/keys :req [
                ::stack-ops ::call-stack
                ::cmp-refs ::op-sources
                ::flow-sources ::mov-sources
                ::cond-depth]))

(s/def ::target ::op-ref)
(s/def ::sources (s/coll-of
                  ::op-ref
                  :into #{}
                  :max-count num-opcodes))

(s/def ::mut (s/map-of ::mem-ref
                       (s/coll-of
                        (s/fspec
                         :args (s/cat
                                ::state ::state)
                         :ret ::state)
                        :kind set?)))

(s/def ::cond (s/keys :req [
                            ::op-ref ::sources
                            ::line]))

(s/def ::redir ::line)

(s/def ::flow (s/keys :req []
                      :opt [::cond ::redir]))

(s/def ::instr (s/keys :req [::target ::mut]
                       :opt [::flow]))

;; function specs

(s/fdef proc
  :args (s/cat ::fname-bin string?)
  :ret ::graph)

(s/fdef proc-asm
  :args (s/cat ::fname-asm string?)
  :ret (partial satisfies? ;; impl WritePort<::graph>
                clojure.core.async.impl.protocols/WritePort))

(s/def ::proc-segm-fn
  (s/fspec :args (s/cat ::state ::state
                        ::state-meta ::state-meta
                        ::track-mut ::track-mut)
           :ret (s/coll-of ::proc-segm-fn
                           :kind set?)))

(s/fdef proc-segm
  :args (s/cat ::state ::state
               ::state-meta ::state-meta
               ::track-mut ::track-mut)
  :ret (s/coll-of ::proc-segm-fn
                  :kind set?))

(s/fdef parse-instr
  :args string?
  :ret ::instr)

(s/fdef mk-linker
  :args (s/cat ::sources ::sources
               ::target ::target)
  :ret (s/fspec
        :args (s/cat ::graph ::graph)
        :ret ::graph))

;; function definitions

(defn proc-asm
  ([fname-asm]
   (let [
         file-vec (iota/vec fname-asm)
         graph-agent (-> (repeat num-opcodes #{})
                         (vec)
                         (with-meta {
                                     ::graph-complete false})
                         (agent))
         num-active (atom 1)
         jump-history (atom #{})]
     (let [
           segm-proc (partial proc-segm file-vec)
           trampoline-channel (async/chan
                               trampoline-channel-size)
           result-channel (async/chan 1)]
       (do
         (async/put! trampoline-channel
                     (segm-proc {} {
                                    ::flow-src []
                                    ::cond-depth 0
                                    ::pos 0}
                                {
                                 ::graph-agent
                                 graph-agent
                                 ::num-active
                                 num-active
                                 ::jump-history
                                 jump-history}))
         (async/go-loop []
           (when-let [
                      fns (async/<! trampoline-channel)]
             (do
               (doall
                (map #(async/go
                        (async/>! trampoline-channel
                                  (%)))))
               (recur))))
         (async/go-loop [
                         target ::trampoline-channel
                         active? true]
           (async/<! (async/timeout 500 #_ms))
           (case target
             ::trampoline-channel
             (if (zero? @num-active)
               (do
                 (async/close! trampoline-channel)
                 (recur ::graph-agent false))
               (recur ::trampoline-channel true))
             ::graph-agent
             (if-not active?
               (do
                 (send graph-agent vary-meta
                       #(assoc % ::graph-complete true))
                 (recur ::graph-agent true))
               (if ((comp ::graph-complete meta) @graph-agent)
                 (do
                   (await graph-agent)
                   (async/>! result-channel @graph-agent))
                 (recur ::graph-agent true)))))
           result-channel)))))

