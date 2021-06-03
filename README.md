# wanderer
Represent a binary as a graph (of opcodes)

<br>
To process a single file (highly parallelized), use `#'wanderer.core/proc-file`:<br>
```clojure
(s/fdef proc-file
  :args (s/cat ::fname-bin string?)
  :ret ::graph)
```

To process multiple files, don't just run `#'wanderer.core/proc-file` over and over again.<br>
Instead, use `#'wanderer.core/proc-files`:<br>
```clojure
(s/fdef proc-files
  :args (s/cat ::set-fname-bin
               (s/coll-of ::fname-bin
                          :kind (complement set?))
               ::fname-target (s/? string?))
  :ret nil?)
```
Instead of returning the graph, it serializes the graph with modified MessagePack (`.mmp`).<br>
Then it adds it as a new line to the file specified as `:wanderer.core/fname-target`.<br>
By default, this is `target.mmp`.<br>
