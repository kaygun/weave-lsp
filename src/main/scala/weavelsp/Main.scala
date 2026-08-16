package weavelsp

import weavelsp.model.BufferStore
import weavelsp.parser.MarkdownParser
import weavelsp.piping.PipelineEngine
import weavelsp.runner.LanguageRegistry
import weavelsp.lsp.LspServerManager

import java.nio.file.{Files, Paths}
import scala.annotation.tailrec

object Main:

  case class CliArgs(
    inputFile: Option[String] = None,
    outputFile: Option[String] = None,
    configPath: String = "languages.json"
  )

  def main(args: Array[String]): Unit =
    if args.isEmpty || args.contains("--help") || args.contains("-h") then
      printUsage()
      sys.exit(0)

    val cliArgs = parseArgs(args.toList, CliArgs())

    val inputPathStr = cliArgs.inputFile.getOrElse {
      println("[ERROR] No input Markdown file specified.")
      printUsage()
      sys.exit(1)
    }

    val inputPath = Paths.get(inputPathStr)
    if !Files.exists(inputPath) then
      println(s"[ERROR] File not found: $inputPathStr")
      sys.exit(1)

    val markdownContent = Files.readString(inputPath)
    println(s"=== weave-lsp processing '$inputPathStr' ===")

    val registry = LanguageRegistry.loadFromFile(cliArgs.configPath)
    val lspManager = new LspServerManager(registry)
    val pipelineEngine = new PipelineEngine(registry, lspManager)
    val bufferStore = new BufferStore()

    try
      val doc = MarkdownParser.parseDocument(markdownContent)
      val executedDoc = pipelineEngine.executePipeline(doc, bufferStore)
      val renderedMarkdown = MarkdownParser.renderDocument(executedDoc, bufferStore)

      val targetOutputPath = cliArgs.outputFile.getOrElse(defaultOutputPath(inputPathStr))
      Files.writeString(Paths.get(targetOutputPath), renderedMarkdown)
      println(s"=== Output successfully written to '$targetOutputPath' ===")

    catch
      case ex: Throwable =>
        println(s"[ERROR] An unexpected error occurred: ${ex.getMessage}")
        ex.printStackTrace()
    finally
      println("=== Cleaning up persistent processes and LSP servers ===")
      pipelineEngine.shutdown()

  @tailrec
  private def parseArgs(args: List[String], acc: CliArgs): CliArgs =
    args match
      case ("-o" | "--output") :: out :: rest =>
        parseArgs(rest, acc.copy(outputFile = Some(out)))
      case ("-c" | "--config") :: cfg :: rest =>
        parseArgs(rest, acc.copy(configPath = cfg))
      case arg :: rest if !arg.startsWith("-") && acc.inputFile.isEmpty =>
        parseArgs(rest, acc.copy(inputFile = Some(arg)))
      case _ :: rest =>
        parseArgs(rest, acc)
      case Nil =>
        acc

  private def defaultOutputPath(inputPathStr: String): String =
    val path = Paths.get(inputPathStr)
    val fileName = path.getFileName.toString
    val dotIdx = fileName.lastIndexOf('.')
    if dotIdx > 0 then
      s"${fileName.substring(0, dotIdx)}_executed.md"
    else
      s"${fileName}_executed.md"

  private def printUsage(): Unit =
    println(
      """
        |weave-lsp - Polyglot Markdown Notebook Processor with Unix Data Piping & LSP
        |
        |Usage:
        |  scala-cli run . -- <input_notebook.md> [-o output_notebook.md] [-c languages.json]
        |
        |Options:
        |  -o, --output <file>  Specify output Markdown file (default: <input>_executed.md)
        |  -c, --config <file>  Path to custom languages.json config file
        |  -h, --help           Show this help message
      """.stripMargin
    )
