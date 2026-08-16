# weave-lsp

> **Polyglot Markdown Notebook Engine with Cross-Language Unix Data Piping & Real-Time LSP Integration**

`weave-lsp` is a lightweight, extensible Scala 3 notebook processor that transforms standard Markdown files into executable polyglot pipelines. Code cells written in different programming languages—such as Bash, Python, Clojure, Common Lisp, Scala, and Idris 2—can execute statefully, pipe data buffers seamlessly across language boundaries, and validate syntax live via Language Server Protocol (LSP) connections.

---

## Key Features

- **Polyglot Stateful Execution**: Run cells across Bash, Python, Clojure, Common Lisp (SBCL), Scala, Idris 2, and Node.js. State is preserved in long-running language sessions across cell boundaries.
- **Declarative Unix-Style Data Piping**: Pipe stdout/data buffers produced by one cell (`name:cell1`) directly into downstream cells (`input:cell1`) regardless of language differences.
- **LSP Diagnostics & Virtual Documents**: Integrates with language servers (`metals`, `pyright`, `clojure-lsp`, `cl-lsp`, `idris2-lsp`) via JSON-RPC, exposing virtual document buffers for real-time code diagnostics.
- **Granular Code & Output Visibility**: Control document presentation per cell using attributes (`code:visible|hidden`, `output:visible|hidden`). Hidden cells continue to execute and maintain pipeline state while staying invisible in rendered Markdown.
- **Dedicated Display Blocks**: Decouple cell execution from document presentation using ` ```render:<cell_name> ` blocks for clean output placement.
- **File & Media Artifact Handling**: Support cells generating binary file artifacts (e.g. SVG plots, JSON datasets, model binaries) that are embedded into Markdown or processed by downstream cells.
- **Default CSS Styling (`mlisp.css`) & MathJax Support**: Standardized CSS styling bundled with the repository for HTML export via Pandoc with MathJax LaTeX math rendering.
- **Declarative Language Registry (`languages.json`)**: Configure language binaries, REPL flags, sentinel templates, and input buffer injection preambles without modifying core engine code.

---

## Architecture Overview

```
                      +-------------------------+
                      |   Markdown Notebook     |
                      +------------+------------+
                                   |
                                   v
                      +-------------------------+
                      |     MarkdownParser      |
                      +------------+------------+
                                   | AST (CodeCell, TextCell, RenderCell)
                                   v
                      +-------------------------+
                      |    DependencyGraph      | (Topological Sort)
                      +------------+------------+
                                   | Ordered Execution Plan
                                   v
                      +-------------------------+
                      |     PipelineEngine      | <---> LspServerManager (JSON-RPC)
                      +------------+------------+
                                   | Executed Buffers
                                   v
                      +-------------------------+
                      |  Rendered Markdown Doc  |
                      +-------------------------+
```

---

## Quickstart & Installation

### Prerequisites

- **Java JDK**: Version 17 or higher
- **Scala CLI**: `scala-cli` installed on `PATH`
- Language binaries installed as desired (e.g. `python3`, `clojure`, `sbcl`, `idris2`, `scala-cli`)

### Running Tests

```bash
scala-cli test .
```

### Running a Notebook

```bash
# Execute sample pipeline
scala-cli run . -- examples/sample_pipeline.md -o artefacts/output.md

# Execute polyglot artifact workflow
scala-cli run . -- examples/polyglot_artifacts.md -o artefacts/output_artifacts.md
```

### Exporting Executed Markdown to HTML via Pandoc with `mlisp.css` and MathJax

```bash
pandoc artefacts/output_centralizers.md -s -c mlisp.css --mathjax -o artefacts/output_centralizers.html
```

---

## Notebook Syntax Reference

### Code Cell Definition

```markdown
```name:fetch_data lang:bash type:json code:visible output:hidden
echo '[{"id": 1, "score": 95}, {"id": 2, "score": 88}]'
```
```

#### Supported Header Attributes

| Attribute | Description | Default |
| :--- | :--- | :--- |
| `name:<id>` | Unique identifier for the cell's output buffer | Optional |
| `lang:<language>` | Language identifier (`bash`, `python`, `clojure`, `lisp`, `scala`, `idris`) | `bash` |
| `input:<id1>,<id2>` | Input buffer dependency piped into this cell | None |
| `type:<format>` | Buffer content type (`json`, `xml`, `text`) | `text` |
| `code:<vis>` | Code block visibility (`visible` or `hidden`) | `visible` |
| `output:<vis>` | Inline output block visibility (`visible` or `hidden`) | `visible` |

### Downstream Piping Example

```markdown
```name:process_python input:fetch_data lang:python type:json code:visible output:hidden
import os, json
data = json.loads(os.getenv("WEAVE_INPUT"))
top = [u for u in data if u["score"] > 90]
print(json.dumps(top))
```
```

### Display Blocks

To render a stored buffer at a specific location in the document:

```markdown
```render:process_python type:json
```
```

---

## Included Examples (`examples/`)

1. **`examples/sample_pipeline.md`**: Multi-stage data pipeline passing raw JSON across Bash $\to$ Python $\to$ Clojure $\to$ Common Lisp.
2. **`examples/centralizers.md`**: Group theory computations (permutation cycles & integer partitions) adapted from Atabey Kaygun's blog post.
3. **`examples/boyer_moore_misra_gries.md`**: Streaming majority algorithms in Clojure.
4. **`examples/associahedra.md`**: Loday integer coordinates for associahedra Catalan binary trees in Scala.
5. **`examples/idris_proof.md`**: Verified theorem proving for natural number addition commutativity in Idris 2.
6. **`examples/polyglot_artifacts.md`**: Python vector SVG generation and cross-language file reading in Clojure & Common Lisp.
7. **`examples/counting_graphs_scala.md`**: Recursive graph counting with memoized fixed-point combinators and OEIS regular graph sequences in Scala.

Executed Markdown output files are generated directly into [`artefacts/`](artefacts/).

---

## Declarative Language Configuration (`languages.json`)

`weave-lsp` is fully configurable via `languages.json`:

```json
{
  "languages": {
    "clojure": {
      "lspCommand": ["clojure-lsp"],
      "runnerCommand": ["clojure", "-M"],
      "sentinelTemplate": "(do (println \"%s\") (flush))",
      "fileExtension": ".clj",
      "inputBufferTemplate": "(System/setProperty \"WEAVE_INPUT\" (String. (.decode (java.util.Base64/getDecoder) (.getBytes \"%s\" \"UTF-8\")) \"UTF-8\"))"
    }
  }
}
```

---

## License

MIT License. Developed by Atabey Kaygun.
