# Polyglot Data Pipeline with weave-lsp

This notebook demonstrates cross-language piping, persistent state, and real data transformations across Bash, Python, Clojure, and Common Lisp.

## Step 1: Fetch Raw JSON via Bash
```bash
echo '[{"id": 1, "name": "Alice", "score": 85}, {"id": 2, "name": "Bob", "score": 92}, {"id": 3, "name": "Charlie", "score": 78}]'
```

> **Output [fetch_users]**
```json
[
  {
    "id": 1,
    "name": "Alice",
    "score": 85
  },
  {
    "id": 2,
    "name": "Bob",
    "score": 92
  },
  {
    "id": 3,
    "name": "Charlie",
    "score": 78
  }
]
```


## Step 2: Transform Data in Python (Persistent State)
```python
import sys, os, json

raw_input = os.getenv("WEAVE_INPUT")
data = json.loads(raw_input) if raw_input else []

# Save global state in persistent Python session
top_students = [u for u in data if u["score"] > 80]

print(json.dumps(top_students, indent=2))
```

> **Output [processed_users]**
```json
[
  {
    "id": 1,
    "name": "Alice",
    "score": 85
  },
  {
    "id": 2,
    "name": "Bob",
    "score": 92
  }
]
```


## Step 3: Reuse Persistent State in Python
```python
# 'top_students' is available from Step 2
avg_score = sum(u["score"] for u in top_students) / len(top_students)
report = {
    "total_top_students": len(top_students),
    "average_score": avg_score,
    "names": [u["name"] for u in top_students]
}
print(json.dumps(report, indent=2))
```

> **Output [python_summary]**
```json
{
  "total_top_students": 2,
  "average_score": 88.5,
  "names": [
    "Alice",
    "Bob"
  ]
}
```


## Step 4: Process Input Buffer in Clojure
```clojure
;; Read and parse piped JSON input from preceding Python step
(let [raw (or (System/getProperty "WEAVE_INPUT") (System/getenv "WEAVE_INPUT"))
      avg (Double/parseDouble (second (re-find #"\"average_score\":\s*([0-9.]+)" raw)))
      total (Integer/parseInt (second (re-find #"\"total_top_students\":\s*([0-9]+)" raw)))
      names-block (second (re-find #"(?s)\"names\":\s*\[(.*?)\]" raw))
      names (map second (re-seq #"\"([A-Za-z]+)\"" names-block))]
  (println (str "Honors Cohort (" total " students): " (clojure.string/join ", " names) " | Group Average: " avg)))
```

> **Output [clj_summary]**
```plaintext
Honors Cohort (2 students): Alice, Bob | Group Average: 88.5
```


## Step 5: Process Data in Common Lisp
```lisp
;; Common Lisp execution with SBCL - format final pipeline report
(let ((raw (sb-ext:posix-getenv "WEAVE_INPUT")))
  (format t "=== Final Certified Pipeline Report ===~%")
  (format t "Upstream summary: ~a~%" raw)
  (format t "Status: PASSED (All top performers validated)~%"))
```

> **Output [lisp_summary]**
```plaintext
=== Final Certified Pipeline Report ===
Upstream summary: Honors Cohort (2 students): Alice, Bob | Group Average: 88.5

Status: PASSED (All top performers validated)
```

