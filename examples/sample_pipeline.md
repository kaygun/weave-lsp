# Polyglot Data Pipeline with weave-lsp

This notebook demonstrates cross-language piping, persistent state, and dedicated buffer rendering across Bash, Python, Clojure, and Common Lisp.

## Step 1: Fetch Raw JSON via Bash
```name:fetch_users lang:bash type:json output:hidden
echo '[{"id": 1, "name": "Alice", "score": 85}, {"id": 2, "name": "Bob", "score": 92}, {"id": 3, "name": "Charlie", "score": 78}]'
```

## Step 2: Transform Data in Python (Persistent State)
```name:processed_users input:fetch_users lang:python type:json output:hidden
import sys, os, json

raw_input = os.getenv("WEAVE_INPUT")
data = json.loads(raw_input) if raw_input else []

# Save global state in persistent Python session
top_students = [u for u in data if u["score"] > 80]

print(json.dumps(top_students))
```

## Step 3: Reuse Persistent State in Python
```name:python_summary lang:python type:json output:hidden
# 'top_students' is available from Step 2
avg_score = sum(u["score"] for u in top_students) / len(top_students)
report = {
    "total_top_students": len(top_students),
    "average_score": avg_score,
    "names": [u["name"] for u in top_students]
}
print(json.dumps(report))
```

## Step 4: Process Input Buffer in Clojure
```name:clj_summary input:fetch_users lang:clojure type:json output:hidden
;; Read piped input buffer from WEAVE_INPUT system property or env var
(let [raw-input (or (System/getProperty "WEAVE_INPUT") (System/getenv "WEAVE_INPUT"))]
  (println (str "{\"clojure_status\": \"ok\", \"input_length\": " (count (or raw-input "")) "}")))
```

## Step 5: Process Data in Common Lisp
```name:lisp_summary input:fetch_users lang:lisp type:json output:hidden
;; Common Lisp execution with SBCL
(let ((raw-input (sb-ext:posix-getenv "WEAVE_INPUT")))
  (format t "{\"lisp_status\": \"ok\", \"engine\": \"sbcl\", \"bytes_received\": ~a}" (length (or raw-input ""))))
```

## Step 6: Render Outputs via Dedicated Display Blocks

### Python Processed Report
```render:python_summary type:json
```

### Clojure Processed Report
```render:clj_summary type:json
```

### Lisp Processed Report
```render:lisp_summary type:json
```
