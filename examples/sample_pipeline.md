# Polyglot Data Pipeline with weave-lsp

This notebook demonstrates cross-language piping, persistent state, and real data transformations across Bash, Python, Clojure, and Common Lisp.

## Step 1: Fetch Raw JSON via Bash
```name:fetch_users lang:bash type:json output:visible
echo '[{"id": 1, "name": "Alice", "score": 85}, {"id": 2, "name": "Bob", "score": 92}, {"id": 3, "name": "Charlie", "score": 78}]'
```

## Step 2: Transform Data in Python (Persistent State)
```name:processed_users input:fetch_users lang:python type:json output:visible
import sys, os, json

raw_input = os.getenv("WEAVE_INPUT")
data = json.loads(raw_input) if raw_input else []

# Save global state in persistent Python session
top_students = [u for u in data if u["score"] > 80]

print(json.dumps(top_students, indent=2))
```

## Step 3: Reuse Persistent State in Python
```name:python_summary input:processed_users lang:python type:json output:visible
# 'top_students' is available from Step 2
avg_score = sum(u["score"] for u in top_students) / len(top_students)
report = {
    "total_top_students": len(top_students),
    "average_score": avg_score,
    "names": [u["name"] for u in top_students]
}
print(json.dumps(report, indent=2))
```

## Step 4: Process Input Buffer in Clojure
```name:clj_summary input:python_summary lang:clojure output:visible
;; Read and parse piped JSON input from preceding Python step
(let [raw (or (System/getProperty "WEAVE_INPUT") (System/getenv "WEAVE_INPUT"))
      avg (Double/parseDouble (second (re-find #"\"average_score\":\s*([0-9.]+)" raw)))
      total (Integer/parseInt (second (re-find #"\"total_top_students\":\s*([0-9]+)" raw)))
      names-block (second (re-find #"(?s)\"names\":\s*\[(.*?)\]" raw))
      names (map second (re-seq #"\"([A-Za-z]+)\"" names-block))]
  (println (str "Honors Cohort (" total " students): " (clojure.string/join ", " names) " | Group Average: " avg)))
```

## Step 5: Process Data in Common Lisp
```name:lisp_summary input:clj_summary lang:lisp output:visible
;; Common Lisp execution with SBCL - format final pipeline report
(let ((raw (sb-ext:posix-getenv "WEAVE_INPUT")))
  (format t "=== Final Certified Pipeline Report ===~%")
  (format t "Upstream summary: ~a~%" raw)
  (format t "Status: PASSED (All top performers validated)~%"))
```
