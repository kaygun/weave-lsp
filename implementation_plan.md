# Implementation Plan - Refactor `weave-lsp` Codebase

Refactor the `weave-lsp` codebase for coherence, clarity, and maintainability without altering existing functionality or breaking test correctness.

## Proposed Refactorings & Abstractions

### 1. Model & Content Formatting (`weavelsp.model`)
- **[MODIFY] [Buffer.scala](file:///home/kaygun/src/scala/universal-lsp/src/main/scala/weavelsp/model/Buffer.scala)**:
  - Move output formatting logic (`formatOutput` for JSON, XML, and PlainText) directly into `ContentType` (e.g. `ContentType.format(raw: String)`).
  - Add utility methods on `Visibility` and `ContentType` for clean parsing and string conversion.

### 2. Header Parsing Abstraction (`weavelsp.parser`)
- **[MODIFY] [BlockHeaderParser.scala](file:///home/kaygun/src/scala/universal-lsp/src/main/scala/weavelsp/parser/BlockHeaderParser.scala)**:
  - Extract reusable key-value token parsing (`parseKeyValuePairs(headerText)`) shared between `parseCodeBlockHeader` and `parseRenderHeader`.
  - Replace repetitive `if token.contains(":")` splits with a single helper.
- **[MODIFY] [MarkdownParser.scala](file:///home/kaygun/src/scala/universal-lsp/src/main/scala/weavelsp/parser/MarkdownParser.scala)**:
  - Delegate formatting directly to `ContentType.format(...)`.
  - Simplify code block header assembly and cell rendering logic.

### 3. Declarative Language Spec & Code Preparation (`weavelsp.runner`)
- **[MODIFY] [LanguageConfig.scala](file:///home/kaygun/src/scala/universal-lsp/src/main/scala/weavelsp/runner/LanguageConfig.scala)**:
  - Add alias resolution (`canonicalName`) and language type checkers (`isClojure`, `isPython`, `isBash`, `isLisp`, `isNode`) to `LangSpec` / `LanguageRegistry`.
- **[MODIFY] [GenericProcessSession.scala](file:///home/kaygun/src/scala/universal-lsp/src/main/scala/weavelsp/runner/GenericProcessSession.scala)**:
  - Abstract input buffer code preparation into a dedicated method (`prepareCode(code, activeBuffer)`).
  - Clean up process execution, stdout line reading, prompt filtering, and temporary file management.

### 4. LSP Infrastructure (`weavelsp.lsp`)
- **[MODIFY] [LspClient.scala](file:///home/kaygun/src/scala/universal-lsp/src/main/scala/weavelsp/lsp/LspClient.scala)**:
  - Consolidate JSON-RPC message construction for notifications and requests.
  - Simplify stdio header and payload reader.

### 5. CLI Entry Point (`weavelsp.Main`)
- **[MODIFY] [Main.scala](file:///home/kaygun/src/scala/universal-lsp/src/main/scala/weavelsp/Main.scala)**:
  - Refactor manual `while` loop CLI argument parsing into idiomatic tail-recursive argument matching.

---

## Verification Plan

### Automated Tests
- Run `scala-cli test .` to verify all MUnit tests (`ParserAndPipingSpec`) pass without regressions.

### Manual Verification
- Run `scala-cli run . -- examples/sample_pipeline.md -o output.md` to verify end-to-end multi-language notebook processing (Bash -> Python -> Clojure -> Common Lisp) produces valid JSON reports in `output.md`.
