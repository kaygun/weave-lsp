package weavelsp

import munit.FunSuite
import weavelsp.model.*
import weavelsp.runner.*
import weavelsp.piping.PipelineEngine
import weavelsp.lsp.LspServerManager
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

class ExecutionSpec extends FunSuite:
  private val registry = new LanguageRegistry(LanguageRegistry.defaultRegistry.specs.map { (key, spec) =>
    key -> spec.copy(lspCommand = Nil)
  })

  private def withSession(language: String, timeout: Long = 10000)(f: GenericProcessSession => Unit): Unit =
    val session = new GenericProcessSession(language, registry.getSpec(language).get.copy(timeoutMillis = timeout), registry)
    try f(session)
    finally session.close()

  private def input(value: String) = Some(DataBuffer("input", value, ContentType.PlainText))

  test("stdout preserves whitespace, blank lines and prompt-like data") {
    withSession("python") { session =>
      assertEquals(session.eval("print('  >>> data\\n\\nuser=> data  ')", None), Right("  >>> data\n\nuser=> data  \n"))
    }
  }

  test("stderr never contaminates successful data buffers") {
    withSession("python") { session =>
      assertEquals(session.eval("import sys\nprint('diagnostic', file=sys.stderr)\nprint('{\"ok\":true}')", None), Right("{\"ok\":true}\n"))
    }
  }

  test("interpreter errors and shell nonzero exits fail execution") {
    withSession("python") { session =>
      val result = session.eval("raise RuntimeError('broken')", None)
      assert(result.swap.toOption.exists(_.contains("broken")))
    }
    withSession("bash") { session =>
      assert(session.eval("echo broken >&2\nexit 7", None).swap.toOption.exists(_.contains("code 7")))
    }
  }

  test("failed cells are not retained in replay history") {
    withSession("python") { session =>
      assertEquals(session.eval("value = 1", None), Right(""))
      assert(session.eval("raise RuntimeError('broken')", None).isLeft)
      assertEquals(session.eval("print(value + 1)", None), Right("2\n"))
    }
  }

  test("each replayed Python cell sees its original input") {
    withSession("python") { session =>
      assertEquals(session.eval("import os\noriginal = os.environ['WEAVE_INPUT']\nprint(original)", input("first")), Right("first\n"))
      assertEquals(session.eval("print(original + ':' + os.environ['WEAVE_INPUT'])", input("second")), Right("first:second\n"))
      assertEquals(session.eval("print(os.getenv('WEAVE_INPUT', 'absent'))", None), Right("absent\n"))
    }
  }

  test("Bash input snapshots safely preserve quotes and newlines") {
    withSession("bash") { session =>
      val value = "héllo 'quoted' $literal\n\n"
      assertEquals(session.eval("original=$WEAVE_INPUT\nprintf '%s' \"$original\"", input(value)), Right(value))
      assertEquals(session.eval("printf '%s|%s' \"$original\" \"$WEAVE_INPUT\"", input("next")), Right(value + "|next"))
    }
  }

  test("output boundaries work when replay changes the number of printed lines") {
    val file = Files.createTempFile("weave-counter-", ".txt")
    Files.writeString(file, "0")
    try
      withSession("python") { session =>
        val path = ujson.write(file.toString)
        val code = s"from pathlib import Path\np = Path($path)\nn = int(p.read_text()) + 1\np.write_text(str(n))\nprint('old\\n' * n, end='')"
        assertEquals(session.eval(code, None), Right("old\n"))
        assertEquals(session.eval("print('new', end='')", None), Right("new"))
      }
    finally Files.deleteIfExists(file)
  }

  test("cell execution has a bounded timeout") {
    withSession("bash", timeout = 200) { session =>
      val start = System.nanoTime()
      assert(session.eval("sleep 10", None).swap.toOption.exists(_.contains("timed out")))
      assert((System.nanoTime() - start) / 1000000 < 3000)
    }
  }

  test("independent execution avoids replaying side effects") {
    val file = Files.createTempFile("weave-once-", ".txt")
    val spec = registry.getSpec("python").get.copy(replayPreviousCells = false)
    val session = new GenericProcessSession("python", spec, registry)
    try
      assertEquals(session.eval(s"open(${ujson.write(file.toString)}, 'a').write('x')", None), Right(""))
      assertEquals(session.eval("print('done')", None), Right("done\n"))
      assertEquals(Files.readString(file), "x")
    finally
      session.close()
      Files.deleteIfExists(file)
  }

  private def withEngine(f: PipelineEngine => Unit): Unit =
    val engine = new PipelineEngine(registry, new LspServerManager(registry))
    try f(engine)
    finally engine.shutdown()

  private def cell(name: String, code: String, inputs: List[String] = Nil) =
    CodeCell(BlockAttributes(name = Some(name), lang = Some("bash"), inputs = inputs), code)

  test("missing inputs fail before any cells execute") {
    withEngine { engine =>
      val store = new BufferStore()
      intercept[IllegalArgumentException] {
        engine.executePipeline(NotebookDocument(List(cell("first", "echo yes"), cell("last", "cat", List("missing")))), store)
      }
      assertEquals(store.getAll, Map.empty[String, DataBuffer])
    }
  }

  test("external input buffers are accepted") {
    withEngine { engine =>
      val store = new BufferStore()
      store.put(DataBuffer("external", "hello", ContentType.PlainText))
      engine.executePipeline(NotebookDocument(List(cell("result", "printf '%s' \"$WEAVE_INPUT\"", List("external")))), store)
      assertEquals(store.get("result").map(_.content), Some("hello"))
    }
  }

  test("failed producers stop consumers and never publish an output buffer") {
    withEngine { engine =>
      val store = new BufferStore()
      intercept[IllegalStateException] {
        engine.executePipeline(NotebookDocument(List(cell("fail", "exit 4"), cell("next", "echo wrong", List("fail")))), store)
      }
      assertEquals(store.getAll, Map.empty[String, DataBuffer])
    }
  }

  test("multiple input declarations fail rather than silently dropping data") {
    withEngine { engine =>
      val store = new BufferStore()
      val doc = NotebookDocument(List(cell("a", "echo a"), cell("b", "echo b"), cell("c", "echo c", List("a", "b"))))
      intercept[IllegalArgumentException](engine.executePipeline(doc, store))
      assertEquals(store.getAll, Map.empty[String, DataBuffer])
    }
  }

  private def withDirectory(f: Path => Unit): Unit =
    val dir = Files.createTempDirectory("weave-cli-")
    try f(dir)
    finally
      val paths = Files.walk(dir)
      try paths.iterator().asScala.toList.reverse.foreach(Files.deleteIfExists(_))
      finally paths.close()

  test("CLI failure returns nonzero and preserves an existing output") {
    withDirectory { dir =>
      val notebook = dir.resolve("input.md")
      val output = dir.resolve("output.md")
      val config = dir.resolve("languages.json")
      Files.writeString(config, """{"languages":{"bash":{"runnerCommand":["bash"],"fileExtension":".sh"}}}""")
      Files.writeString(notebook, "```name:fail lang:bash\nexit 9\n```\n")
      Files.writeString(output, "keep me")
      assertEquals(Main.run(Array(notebook.toString, "-o", output.toString, "-c", config.toString)), 1)
      assertEquals(Files.readString(output), "keep me")
    }
  }

  test("CLI rejects missing option values, unknown flags and extra files") {
    for args <- List(Array("-o"), Array("-c"), Array("file.md", "--typo"), Array("one.md", "two.md")) do
      assertEquals(Main.run(args), 1)
  }

  test("CLI renders a Bash to Python pipeline into a new output directory") {
    withDirectory { dir =>
      val notebook = dir.resolve("input.md")
      val output = dir.resolve("nested/output.md")
      val config = dir.resolve("languages.json")
      Files.writeString(config, """{"languages":{"bash":{"runnerCommand":["bash"],"fileExtension":".sh"},"python":{"runnerCommand":["python3","-u"],"fileExtension":".py","sentinelTemplate":"print(\"%s\")"}}}""")
      Files.writeString(notebook, """```name:source lang:bash output:hidden
        |printf '[1,2,3]'
        |```
        |```name:total input:source lang:python output:hidden
        |import json, os
        |print(sum(json.loads(os.environ['WEAVE_INPUT'])))
        |```
        |```render:total
        |```
        |""".stripMargin)
      assertEquals(Main.run(Array(notebook.toString, "-o", output.toString, "-c", config.toString)), 0)
      val result = Files.readString(output)
      assert(result.contains("```plaintext\n6\n```"))
      assert(!result.contains("Output [source]"))
    }
  }

  test("invalid and explicitly missing configuration files fail clearly") {
    withDirectory { dir =>
      val config = dir.resolve("languages.json")
      intercept[IllegalArgumentException](LanguageRegistry.loadFromFile(config.toString))
      assert(LanguageRegistry.loadFromFile(config.toString, allowMissing = true).getSpec("r").isDefined)
      Files.writeString(config, "not json")
      intercept[IllegalArgumentException](LanguageRegistry.loadFromFile(config.toString))
      Files.writeString(config, """{"languages":{"python":{}}}""")
      intercept[IllegalArgumentException](LanguageRegistry.loadFromFile(config.toString))
    }
  }

  test("Python future imports work with input binding and replay") {
    withSession("python") { session =>
      assertEquals(session.eval("from __future__ import annotations\nx: UnknownType = 3", input("first")), Right(""))
      assertEquals(session.eval("from __future__ import annotations\nprint(x)", input("second")), Right("3\n"))
    }
  }

  test("Clojure cell without input clears WEAVE_INPUT and retains persistent state") {
    withSession("clojure", timeout = 30000) { session =>
      assertEquals(session.eval("(def state-val 42)", None), Right(""))
      assertEquals(session.eval("(println (str state-val \":\" (or (System/getProperty \"WEAVE_INPUT\") \"nil\")))", None), Right("42:nil\n"))
      assertEquals(session.eval("(println (System/getProperty \"WEAVE_INPUT\"))", input("hello-clj")), Right("hello-clj\n"))
      assertEquals(session.eval("(println (or (System/getProperty \"WEAVE_INPUT\") \"cleared\"))", None), Right("cleared\n"))
    }
  }

