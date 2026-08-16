# Boyer–Moore and Misra-Gries Algorithms in Clojure

Adopted from Atabey Kaygun's blog post: [Boyer–Moore and Misra-Gries Algorithms in Clojure](https://kaygun.github.io/clean/2021-12-05-boyermoore_and_misra-gries_algorithms_in_clojure.html).

## Description of the Problem

The [Boyer–Moore majority algorithm](https://en.wikipedia.org/wiki/Boyer%E2%80%93Moore_majority_vote_algorithm) is a probabilistic algorithm that solves the majority problem for any data stream in a single pass using constant memory. Today, I'll do an implementation in Clojure.

## Data Stream Source

First, we generate the stream of data points via Bash:

```name:stream_data lang:bash output:hidden
echo '[0, 1, 1, 0, 2, 2, 2, 0, 0, 0, 1, 0, 2, 0]'
```

## An Implementation of Boyer-Moore in Clojure

Here is the implementation of the Boyer-Moore majority algorithm:

```name:boyer_moore_res input:stream_data lang:clojure code:visible output:hidden
(defn boyer-moore [xs]
  (loop [ys xs
         count 0
         candidate nil]
    (cond (empty? ys) candidate
          (zero? count) (recur (rest ys) 1 (first ys))
          (= (first ys) candidate) (recur (rest ys) (inc count) candidate)
          true (recur (rest ys) (dec count) candidate))))

(let [raw-input (or (System/getProperty "WEAVE_INPUT") (System/getenv "WEAVE_INPUT"))
      data (if raw-input (read-string raw-input) [0 1 1 0 2 2 2 0 0 0 1 0 2 0])]
  (println (boyer-moore data)))
```

## An Extension: Misra-Gries Algorithm

There is an extension of the Boyer-Moore majority algorithm by [Misra and Gries](https://people.csail.mit.edu/rrw/6.045-2019/encalgs-mg.pdf). I'll implement that too:

```name:misra_gries_res input:stream_data lang:clojure code:visible output:hidden
(defn misra-gries [xs k]
  (loop [ys xs
         counts {}]
    (if (empty? ys)
      (keys counts)
      (let [y (first ys)
            zs (rest ys)]
        (cond
          (get counts y false) (recur zs (merge-with + counts {y 1}))
          (< (count counts) k) (recur zs (merge counts {y 1}))
          true (recur zs (->> (reduce-kv (fn [m k v] (assoc m k (dec v))) {} counts)
                              (filter (fn [[k v]] (> v 0)))
                              (into {}))))))))

(let [raw-input (or (System/getProperty "WEAVE_INPUT") (System/getenv "WEAVE_INPUT"))
      data (if raw-input (read-string raw-input) [0 1 1 0 2 2 2 0 0 0 1 0 2 0])]
  (println (vec (misra-gries data 2))))
```

## Execution Results

### Boyer-Moore Majority Result
```render:boyer_moore_res
```

### Misra-Gries Frequent Candidates Result
```render:misra_gries_res
```
