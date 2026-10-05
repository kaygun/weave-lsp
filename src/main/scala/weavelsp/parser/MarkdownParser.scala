package weavelsp.parser

import weavelsp.model.*
import scala.collection.mutable.ArrayBuffer

object MarkdownParser:

  private val openingFence = "^ {0,3}(`{3,}|~{3,})(.*)$".r

  def parseDocument(markdownText: String): NotebookDocument =
    val lines = markdownText.linesIterator.toList
    val cells = ArrayBuffer[NotebookCell]()

    val currentTextLines = ArrayBuffer[String]()
    var inCodeBlock = false
    var currentHeader = ""
    var currentFence = ""
    var openingLine = ""
    val currentCodeLines = ArrayBuffer[String]()

    def flushText(): Unit =
      if currentTextLines.nonEmpty then
        cells += TextCell(currentTextLines.mkString("\n"))
        currentTextLines.clear()

    lines.foreach { line =>
      val trimmed = line.dropWhile(_ == ' ')
      val closesFence = inCodeBlock && line.length - trimmed.length <= 3 &&
        trimmed.takeWhile(_ == currentFence.head).length >= currentFence.length &&
        trimmed.dropWhile(_ == currentFence.head).forall(c => c == ' ' || c == '\t')
      if closesFence then
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
        line match
          case openingFence(fence, header) if fence.head != '`' || !header.contains('`') =>
            flushText()
            inCodeBlock = true
            currentFence = fence
            openingLine = line
            currentHeader = header.trim
            currentCodeLines.clear()
          case _ => currentTextLines += line
    }

    // Keep incomplete fences as literal text; never execute a partial cell.
    if inCodeBlock then
      currentTextLines += openingLine
      currentTextLines ++= currentCodeLines
    flushText()
    NotebookDocument(cells.toList)

  def renderDocument(doc: NotebookDocument, bufferStore: BufferStore): String =
    val sb = new StringBuilder()

    def appendBlock(language: String, content: String): Unit =
      val longestRun = "`+".r.findAllIn(content).map(_.length).maxOption.getOrElse(0)
      val fence = "`" * math.max(3, longestRun + 1)
      sb.append(fence).append(language).append("\n")
      sb.append(content)
      if !content.endsWith("\n") then sb.append("\n")
      sb.append(fence).append("\n")

    doc.cells.foreach {
      case TextCell(text) =>
        sb.append(text).append("\n")

      case CodeCell(attrs, code, outputOpt, exitCode) =>
        val langStr = attrs.lang.getOrElse("")
        if attrs.codeVisibility == Visibility.Visible then
          appendBlock(langStr, code)

        if attrs.outputVisibility == Visibility.Visible then
          outputOpt.orElse(attrs.name.flatMap(bufferStore.get).map(_.content)).foreach { outContent =>
            if outContent.nonEmpty then
              val formatTag = attrs.contentType.toString.toLowerCase
              sb.append(s"\n> **Output [${attrs.name.getOrElse("cell")}]**\n")
              appendBlock(formatTag, attrs.contentType.format(outContent))
          }
        sb.append("\n")

      case RenderCell(bufName, cType, fmt) =>
        val effectiveType = bufferStore.get(bufName).map(_.contentType).filter(_ => cType == ContentType.PlainText).getOrElse(cType)
        val formatTag = effectiveType.toString.toLowerCase
        val content = bufferStore.get(bufName) match
          case Some(buf) =>
            if fmt == "raw" then buf.content else effectiveType.format(buf.content)
          case None =>
            s"<!-- Buffer '$bufName' is empty or not yet generated -->"
        appendBlock(formatTag, content)
        sb.append("\n")
    }

    sb.toString()
