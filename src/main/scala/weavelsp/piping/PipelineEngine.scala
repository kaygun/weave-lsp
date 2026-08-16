package weavelsp.piping

import weavelsp.model.*
import weavelsp.runner.{GenericProcessSession, LanguageRegistry}
import weavelsp.lsp.LspServerManager

import scala.collection.mutable
import scala.util.Try

class PipelineEngine(
  val registry: LanguageRegistry,
  val lspManager: LspServerManager
):

  private val processSessions = mutable.Map[String, GenericProcessSession]()

  private def getOrCreateSession(lang: String): GenericProcessSession =
    val canonicalKey = registry.canonicalName(lang)
    processSessions.getOrElseUpdate(canonicalKey, {
      val spec = registry.getSpec(canonicalKey).getOrElse(
        weavelsp.runner.LangSpec(Nil, List(canonicalKey), "echo \"%s\"", ".txt")
      )
      val session = new GenericProcessSession(canonicalKey, spec, registry)
      session.start()
      session
    })

  def executePipeline(doc: NotebookDocument, bufferStore: BufferStore): NotebookDocument =
    val plan = DependencyGraph.buildExecutionPlan(doc)

    if plan.missingDependencies.nonEmpty then
      println("[WARNING] Some input buffers are not produced by preceding cells:")
      plan.missingDependencies.foreach { case (cellId, missingBufs) =>
        println(s"  Cell '$cellId' requires missing buffer(s): ${missingBufs.mkString(", ")}")
      }

    val updatedCellsMap = mutable.Map[CodeCell, CodeCell]()

    plan.orderedCodeCells.foreach { cell =>
      val lang = cell.attributes.lang.getOrElse("bash")
      val canonicalLang = registry.canonicalName(lang)

      // Notify LSP server of virtual document update
      val ext = registry.getSpec(canonicalLang).map(_.fileExtension).getOrElse(".txt")
      val virtualUri = s"file:///virtual_notebook/${cell.attributes.name.getOrElse("cell")}$ext"
      lspManager.notifyCellUpdated(canonicalLang, virtualUri, cell.code)

      // Fetch input buffer if specified
      val primaryInputBuf = cell.attributes.inputs.headOption.flatMap(bufferStore.get)

      // Execute code via persistent session
      val session = getOrCreateSession(canonicalLang)
      println(s"[EXEC] Running cell '${cell.attributes.name.getOrElse("unnamed")}' ($canonicalLang)...")

      val execResult = session.eval(cell.code, primaryInputBuf)

      execResult match
        case Right(outputStr) =>
          cell.attributes.name.foreach { bufName =>
            val buf = DataBuffer(
              name = bufName,
              content = outputStr,
              contentType = cell.attributes.contentType
            )
            bufferStore.put(buf)
          }

          val updatedCell = cell.copy(output = Some(outputStr), exitCode = 0)
          updatedCellsMap(cell) = updatedCell

        case Left(errMsg) =>
          println(s"[ERROR] Execution failed for cell '${cell.attributes.name.getOrElse("unnamed")}': $errMsg")
          val updatedCell = cell.copy(output = Some(s"ERROR: $errMsg"), exitCode = 1)
          updatedCellsMap(cell) = updatedCell
    }

    val finalCells = doc.cells.map {
      case c: CodeCell if updatedCellsMap.contains(c) => updatedCellsMap(c)
      case other => other
    }

    NotebookDocument(finalCells)

  def shutdown(): Unit =
    processSessions.values.foreach { session =>
      Try(session.close())
    }
    processSessions.clear()
    lspManager.shutdownAll()
