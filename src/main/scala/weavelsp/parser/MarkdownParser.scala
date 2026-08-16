package weavelsp.parser

import weavelsp.model.*
import scala.collection.mutable.ArrayBuffer

object MarkdownParser:

  def parseDocument(markdownText: String): NotebookDocument =
    val lines = markdownText.linesIterator.toList
    val cells = ArrayBuffer[NotebookCell]()

    val currentTextLines = ArrayBuffer[String]()
    var inCodeBlock = false
    var currentHeader = ""
    val currentCodeLines = ArrayBuffer[String]()

    def flushText(): Unit =
      if currentTextLines.nonEmpty then
        cells += TextCell(currentTextLines.mkString("\n"))
        currentTextLines.clear()

    lines.foreach { line =>
      val trimmed = line.trim
      if !inCodeBlock && trimmed.startsWith("```") then
        flushText()
        inCodeBlock = true
        currentHeader = trimmed.stripPrefix("```").trim
        currentCodeLines.clear()
      else if inCodeBlock && trimmed == "```" then
        inCodeBlock = false
        val codeContent = currentCodeLines.mkString("\n")

        BlockHeaderParser.parseRenderHeader(currentHeader) match
          case Some((bufName, cType, fmt)) =>
            cells += RenderCell(bufName, cType, fmt)
          case None =>
            val attrs = BlockHeaderParser.parseCodeBlockHeader(currentHeader)
            cells += CodeCell(attrs, codeContent)
        currentCodeLines.clear()
        currentHeader = ""
      else if inCodeBlock then
        currentCodeLines += line
      else
        currentTextLines += line
    }

    flushText()
    NotebookDocument(cells.toList)

  def renderDocument(doc: NotebookDocument, bufferStore: BufferStore): String =
    val sb = new StringBuilder()

    doc.cells.foreach {
      case TextCell(text) =>
        sb.append(text).append("\n")

      case CodeCell(attrs, code, outputOpt, exitCode) =>
        val langStr = attrs.lang.getOrElse("")
        val headerLine = s"```$langStr"

        if attrs.codeVisibility == Visibility.Visible then
          sb.append(headerLine).append("\n")
          sb.append(code).append("\n")
          sb.append("```\n")

        if attrs.outputVisibility == Visibility.Visible then
          outputOpt.orElse(attrs.name.flatMap(bufferStore.get).map(_.content)).foreach { outContent =>
            if outContent.nonEmpty then
              val formatTag = attrs.contentType.toString.toLowerCase
              sb.append(s"\n> **Output [${attrs.name.getOrElse("cell")}]**\n")
              sb.append("```").append(formatTag).append("\n")
              sb.append(attrs.contentType.format(outContent)).append("\n")
              sb.append("```\n")
          }
        sb.append("\n")

      case RenderCell(bufName, cType, fmt) =>
        val formatTag = cType.toString.toLowerCase
        sb.append(s"```$formatTag\n")
        bufferStore.get(bufName) match
          case Some(buf) =>
            sb.append(cType.format(buf.content)).append("\n")
          case None =>
            sb.append(s"<!-- Buffer '$bufName' is empty or not yet generated -->\n")
        sb.append("```\n\n")
    }

    sb.toString().trim + "\n"
