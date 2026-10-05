package weavelsp

import munit.FunSuite
import weavelsp.model.*
import weavelsp.parser.{BlockHeaderParser, MarkdownParser}
import weavelsp.piping.DependencyGraph

class ParserAndPipingSpec extends FunSuite:

  test("BlockHeaderParser parses key-value attributes correctly") {
    val headerStr = "python name:fetch_api input:prev_buf type:json code:visible output:hidden"
    val attrs = BlockHeaderParser.parseCodeBlockHeader(headerStr)

    assertEquals(attrs.lang, Some("python"))
    assertEquals(attrs.name, Some("fetch_api"))
    assertEquals(attrs.inputs, List("prev_buf"))
    assertEquals(attrs.contentType, ContentType.Json)
    assertEquals(attrs.codeVisibility, Visibility.Visible)
    assertEquals(attrs.outputVisibility, Visibility.Hidden)
  }

  test("BlockHeaderParser parses render display block header") {
    val headerStr = "render:fetch_api type:json format:pretty"
    val renderOpt = BlockHeaderParser.parseRenderHeader(headerStr)

    assert(renderOpt.isDefined)
    val (bufName, cType, fmt) = renderOpt.get
    assertEquals(bufName, "fetch_api")
    assertEquals(cType, ContentType.Json)
    assertEquals(fmt, "pretty")
  }

  test("MarkdownParser parses code cells and text cells") {
    val md =
      """# Sample Title
        |
        |```name:step1 lang:bash type:json output:hidden
        |echo '{"key": "val"}'
        |```
        |
        |```render:step1 type:json
        |```
        |""".stripMargin

    val doc = MarkdownParser.parseDocument(md)
    assertEquals(doc.cells.length, 4)
    assert(doc.cells(0).isInstanceOf[TextCell])
    assert(doc.cells(1).isInstanceOf[CodeCell])
    assert(doc.cells(2).isInstanceOf[TextCell])
    assert(doc.cells(3).isInstanceOf[RenderCell])
  }

  test("DependencyGraph includes hidden code cells in execution plan") {
    val hiddenCodeCell = CodeCell(
      attributes = BlockAttributes(name = Some("hidden1"), codeVisibility = Visibility.Hidden, outputVisibility = Visibility.Hidden),
      code = "echo hidden"
    )
    val visibleCell = CodeCell(
      attributes = BlockAttributes(name = Some("vis1"), inputs = List("hidden1")),
      code = "cat"
    )

    val doc = NotebookDocument(List(visibleCell, hiddenCodeCell))
    val plan = DependencyGraph.buildExecutionPlan(doc)

    val orderedNames = plan.orderedCodeCells.flatMap(_.attributes.name)
    assertEquals(orderedNames, List("hidden1", "vis1"))
  }

  test("MarkdownParser renders code and output according to visibility parameters") {
    val store = new BufferStore()
    store.put(DataBuffer("c1", "hello", ContentType.PlainText))

    val cellVisVis = CodeCell(BlockAttributes(name = Some("c1"), lang = Some("bash"), codeVisibility = Visibility.Visible, outputVisibility = Visibility.Visible), "echo hello", Some("hello"))
    val cellVisHid = CodeCell(BlockAttributes(name = Some("c2"), lang = Some("bash"), codeVisibility = Visibility.Visible, outputVisibility = Visibility.Hidden), "echo hello", Some("hello"))
    val cellHidVis = CodeCell(BlockAttributes(name = Some("c3"), lang = Some("bash"), codeVisibility = Visibility.Hidden, outputVisibility = Visibility.Visible), "echo hello", Some("hello"))
    val cellHidHid = CodeCell(BlockAttributes(name = Some("c4"), lang = Some("bash"), codeVisibility = Visibility.Hidden, outputVisibility = Visibility.Hidden), "echo hello", Some("hello"))

    val doc = NotebookDocument(List(cellVisVis, cellVisHid, cellHidVis, cellHidHid))
    val rendered = MarkdownParser.renderDocument(doc, store)

    assert(rendered.contains("echo hello"))
    assert(rendered.contains("Output [c1]"))
    assert(!rendered.contains("Output [c2]"))
    assert(rendered.contains("Output [c3]"))
    assert(!rendered.contains("Output [c4]"))
  }

  test("DependencyGraph sorts cells in topological order based on input buffers") {
    val cell1 = CodeCell(
      attributes = BlockAttributes(name = Some("cell1")),
      code = "echo 1"
    )
    val cell2 = CodeCell(
      attributes = BlockAttributes(name = Some("cell2"), inputs = List("cell1")),
      code = "cat"
    )
    val cell3 = CodeCell(
      attributes = BlockAttributes(name = Some("cell3"), inputs = List("cell2")),
      code = "cat"
    )

    // Pass cells out of order (cell3, cell1, cell2)
    val doc = NotebookDocument(List(cell3, cell1, cell2))
    val plan = DependencyGraph.buildExecutionPlan(doc)

    val orderedNames = plan.orderedCodeCells.flatMap(_.attributes.name)
    assertEquals(orderedNames, List("cell1", "cell2", "cell3"))
  }

  test("language-only fences are code, not render references") {
    val doc = MarkdownParser.parseDocument("```python\nprint(1)\n```\n")
    val cell = doc.cells.head.asInstanceOf[CodeCell]
    assertEquals(cell.attributes.lang, Some("python"))
    assertEquals(cell.code, "print(1)")
  }

  test("only explicit render and display attributes create render cells") {
    assertEquals(BlockHeaderParser.parseRenderHeader("python name:test"), None)
    assertEquals(BlockHeaderParser.parseRenderHeader("display:test").map(_._1), Some("test"))
  }

  test("long fences preserve nested shorter fences") {
    val md = "````markdown\n```python\nprint(1)\n```\n````\n"
    val doc = MarkdownParser.parseDocument(md)
    assertEquals(doc.cells.size, 1)
    assertEquals(doc.cells.head.asInstanceOf[CodeCell].code, "```python\nprint(1)\n```")
    val rendered = MarkdownParser.renderDocument(doc, new BufferStore())
    assert(rendered.startsWith("````markdown\n"))
    assertEquals(MarkdownParser.parseDocument(rendered).cells.collect { case c: CodeCell => c.code },
      List("```python\nprint(1)\n```"))
  }

  test("tilde fences and longer closing fences are recognized") {
    val doc = MarkdownParser.parseDocument("~~~name:test lang:bash\necho yes\n~~~~\n")
    assertEquals(doc.cells.head.asInstanceOf[CodeCell].attributes.name, Some("test"))
  }

  test("indented and mismatched fences do not close a cell") {
    val doc = MarkdownParser.parseDocument("```bash\n    ```\n~~~\n```\n")
    assertEquals(doc.cells.head.asInstanceOf[CodeCell].code, "    ```\n~~~")
  }

  test("unterminated code is preserved without becoming executable") {
    val md = "text\n```name:danger lang:bash\necho partial\n"
    val doc = MarkdownParser.parseDocument(md)
    assert(doc.cells.forall(_.isInstanceOf[TextCell]))
    assertEquals(MarkdownParser.renderDocument(doc, new BufferStore()), md)
  }

  test("rendered output containing fences remains one fenced block") {
    val store = new BufferStore()
    store.put(DataBuffer("result", "before\n```\nafter\n", ContentType.PlainText))
    val rendered = MarkdownParser.renderDocument(NotebookDocument(List(RenderCell("result"))), store)
    assertEquals(MarkdownParser.parseDocument(rendered).cells.collect { case c: CodeCell => c.code },
      List("before\n```\nafter"))
  }

  test("raw rendering preserves JSON formatting") {
    val store = new BufferStore()
    store.put(DataBuffer("result", "{\"a\":1}", ContentType.Json))
    val rendered = MarkdownParser.renderDocument(
      NotebookDocument(List(RenderCell("result", ContentType.Json, "raw"))), store)
    assert(rendered.contains("{\"a\":1}"))
  }

  test("dependency cycles, self-dependencies and duplicate names are rejected") {
    def cell(name: String, inputs: String*) = CodeCell(BlockAttributes(name = Some(name), inputs = inputs.toList), "")
    for cells <- List(List(cell("a", "b"), cell("b", "a")), List(cell("a", "a")), List(cell("a"), cell("a"))) do
      intercept[IllegalArgumentException] {
        DependencyGraph.buildExecutionPlan(NotebookDocument(cells))
      }
  }

  test("rendering preserves leading Markdown indentation and blank output") {
    val doc = MarkdownParser.parseDocument("    indented code\n")
    assertEquals(MarkdownParser.renderDocument(doc, new BufferStore()), "    indented code\n")
    assertEquals(ContentType.PlainText.format("  \n\n"), "  \n\n")
  }

  test("ContentType.parse and Visibility.parse reject invalid values") {
    intercept[IllegalArgumentException](ContentType.parse("invalid_type"))
    intercept[IllegalArgumentException](Visibility.parse("hiden"))
    intercept[IllegalArgumentException](Visibility.parse("unknown"))
  }

  test("BlockHeaderParser rejects unknown attributes in code blocks and render blocks") {
    intercept[IllegalArgumentException](BlockHeaderParser.parseCodeBlockHeader("python name:test bogus:value"))
    intercept[IllegalArgumentException](BlockHeaderParser.parseRenderHeader("render:test bogus:value"))
  }

  test("BlockHeaderParser parses eval and replay boolean attributes correctly") {
    val attrs1 = BlockHeaderParser.parseCodeBlockHeader("python name:c1 eval:false replay:false")
    assertEquals(attrs1.eval, Some(false))
    assertEquals(attrs1.replay, Some(false))

    val attrs2 = BlockHeaderParser.parseCodeBlockHeader("python name:c2 run:true replay:yes")
    assertEquals(attrs2.eval, Some(true))
    assertEquals(attrs2.replay, Some(true))

    intercept[IllegalArgumentException](BlockHeaderParser.parseCodeBlockHeader("python eval:maybe"))
  }

  test("raw render block preserves buffer content type tag") {
    val store = new BufferStore()
    store.put(DataBuffer("buf1", "{\"status\": \"ok\"}", ContentType.Json))
    val rendered = MarkdownParser.renderDocument(
      NotebookDocument(List(RenderCell("buf1", ContentType.PlainText, "raw"))),
      store
    )
    assert(rendered.contains("```json\n{\"status\": \"ok\"}\n```"))
  }

