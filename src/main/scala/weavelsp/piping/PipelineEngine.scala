package weavelsp.piping

import weavelsp.model.*
import weavelsp.runner.{GenericProcessSession, LanguageRegistry}
import weavelsp.lsp.LspServerManager

import java.nio.file.{Files, Path, Paths}
import scala.collection.mutable
import scala.util.Try

class PipelineEngine(
  val registry: LanguageRegistry,
  val lspManager: LspServerManager,
  val workingDir: Option[Path] = None,
  val enableLsp: Boolean = true
):

  private val processSessions = mutable.Map[String, GenericProcessSession]()
  private val accumulatedCodeByLang = mutable.Map[String, StringBuilder]()

  private def getOrCreateSession(lang: String): GenericProcessSession =
    val canonicalKey = registry.canonicalName(lang)
    processSessions.getOrElseUpdate(canonicalKey, {
      val spec = registry.getSpec(canonicalKey).getOrElse(
        throw new IllegalArgumentException(s"No runner configured for language '$lang'")
      )
      val session = new GenericProcessSession(canonicalKey, spec, registry, workingDir)
      session.start()
      session
    })

  def executePipeline(doc: NotebookDocument, bufferStore: BufferStore): NotebookDocument =
    val plan = DependencyGraph.buildExecutionPlan(doc)

    val missing = plan.missingDependencies.toList.flatMap { (cellId, names) =>
      names.filter(name => bufferStore.get(name).isEmpty).map(name => s"'$cellId' requires '$name'")
    }
    require(missing.isEmpty, s"Missing input buffers: ${missing.mkString(", ")}")
    plan.orderedCodeCells.foreach { cell =>
      val lang = cell.attributes.lang.getOrElse("bash")
      require(registry.getSpec(lang).isDefined, s"No runner configured for language '$lang'")
      require(cell.attributes.inputs.size <= 1,
        s"Cell '${cell.attributes.name.getOrElse("unnamed")}' supports one input buffer; combine inputs in a preceding cell")
    }

    val updatedCellsMap = mutable.Map[CodeCell, CodeCell]()

    plan.orderedCodeCells.foreach { cell =>
      val lang = cell.attributes.lang.getOrElse("bash")
      val canonicalLang = registry.canonicalName(lang)
      val cellName = cell.attributes.name.getOrElse("unnamed")

      // Language server connection stays open, carrying accumulated state across cells
      if enableLsp then
        val ext = registry.getSpec(canonicalLang).map(_.fileExtension).getOrElse(".txt")
        val virtualUri = s"file:///virtual_notebook/$canonicalLang$ext"
        val codeBuffer = accumulatedCodeByLang.getOrElseUpdate(canonicalLang, new StringBuilder())
        if codeBuffer.nonEmpty then codeBuffer.append("\n\n")
        codeBuffer.append(cell.code)
        lspManager.notifyCellUpdated(canonicalLang, virtualUri, codeBuffer.toString())

      // Fetch input buffer if specified
      val primaryInputBuf = cell.attributes.inputs.headOption.flatMap(bufferStore.get)

      // Execute code via persistent session
      val session = getOrCreateSession(canonicalLang)
      println(s"[EXEC] Running cell '$cellName' ($canonicalLang)...")

      val replayCell = cell.attributes.replay.getOrElse(true)
      val execResult = session.eval(cell.code, primaryInputBuf, replayCell)

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

          // If outputFile attribute is specified, write output directly to file
          cell.attributes.outputFile.foreach { relPath =>
            val targetPath = workingDir.map(_.resolve(relPath)).getOrElse(Paths.get(relPath))
            Option(targetPath.getParent).foreach(Files.createDirectories(_))
            Files.writeString(targetPath, outputStr)
          }

          val updatedCell = cell.copy(output = Some(outputStr), exitCode = 0)
          updatedCellsMap(cell) = updatedCell

        case Left(errMsg) =>
          throw new IllegalStateException(s"Execution failed for cell '$cellName': $errMsg")
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
    accumulatedCodeByLang.clear()
    lspManager.shutdownAll()
