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
