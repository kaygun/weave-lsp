package weavelsp

import weavelsp.model.BufferStore
import weavelsp.parser.MarkdownParser
import weavelsp.piping.PipelineEngine
import weavelsp.runner.LanguageRegistry
import weavelsp.lsp.LspServerManager

import java.nio.file.{AtomicMoveNotSupportedException, Files, Path, Paths, StandardCopyOption}
import scala.annotation.tailrec
import scala.util.control.NonFatal

object Main:

  case class CliArgs(
    inputFile: Option[String] = None,
    outputFile: Option[String] = None,
    configPath: String = "languages.json",
    enableLsp: Boolean = true
  )

  def main(args: Array[String]): Unit =
    sys.exit(run(args))

  def run(args: Array[String]): Int =
    if args.isEmpty || args.contains("--help") || args.contains("-h") then
      printUsage()
      return 0

    try
      val cliArgs = parseArgs(args.toList, CliArgs())
      val inputPathStr = cliArgs.inputFile.getOrElse(
        throw new IllegalArgumentException("No input Markdown file specified"))
      val inputPath = Paths.get(inputPathStr).toAbsolutePath
      val markdownContent = Files.readString(inputPath)
      println(s"=== weave-lsp processing '$inputPathStr' ===")

      val explicitConfig = args.contains("-c") || args.contains("--config")
      val registry = LanguageRegistry.loadFromFile(cliArgs.configPath, allowMissing = !explicitConfig)
      val workingDir = Option(inputPath.getParent)
      val pipelineEngine = new PipelineEngine(
        registry = registry,
        lspManager = new LspServerManager(registry),
        workingDir = workingDir,
        enableLsp = cliArgs.enableLsp
      )
      val bufferStore = new BufferStore()
      try
        val doc = MarkdownParser.parseDocument(markdownContent)
        val executedDoc = pipelineEngine.executePipeline(doc, bufferStore)
        val renderedMarkdown = MarkdownParser.renderDocument(executedDoc, bufferStore)
        val targetOutputPath = cliArgs.outputFile.getOrElse(defaultOutputPath(inputPathStr))
        writeOutput(Paths.get(targetOutputPath), renderedMarkdown)
        println(s"=== Output successfully written to '$targetOutputPath' ===")
        0
      finally
        pipelineEngine.shutdown()

    catch
      case NonFatal(ex) =>
        Console.err.println(s"[ERROR] ${ex.getMessage}")
        1

  private def writeOutput(target: Path, content: String): Unit =
    val absoluteTarget = target.toAbsolutePath
    Option(absoluteTarget.getParent).foreach(Files.createDirectories(_))
    val parentDir = Option(absoluteTarget.getParent).getOrElse(Paths.get("."))
    val temporary = Files.createTempFile(parentDir, ".weave-output-", ".md")
    try
      Files.writeString(temporary, content)
      try Files.move(temporary, absoluteTarget, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
      catch
        case _: AtomicMoveNotSupportedException =>
          Files.move(temporary, absoluteTarget, StandardCopyOption.REPLACE_EXISTING)
    finally
      Files.deleteIfExists(temporary)

  @tailrec
  private def parseArgs(args: List[String], acc: CliArgs): CliArgs =
    args match
      case ("-o" | "--output") :: out :: rest if !out.startsWith("-") =>
        parseArgs(rest, acc.copy(outputFile = Some(out)))
      case ("-c" | "--config") :: cfg :: rest if !cfg.startsWith("-") =>
        parseArgs(rest, acc.copy(configPath = cfg))
      case "--no-lsp" :: rest =>
        parseArgs(rest, acc.copy(enableLsp = false))
      case arg :: rest if !arg.startsWith("-") && acc.inputFile.isEmpty =>
        parseArgs(rest, acc.copy(inputFile = Some(arg)))
      case (flag @ ("-o" | "--output" | "-c" | "--config")) :: _ =>
        throw new IllegalArgumentException(s"Missing value for $flag")
      case arg :: _ =>
        throw new IllegalArgumentException(s"Unexpected argument: $arg")
      case Nil =>
        acc

  private def defaultOutputPath(inputPathStr: String): String =
    val path = Paths.get(inputPathStr)
    val fileName = path.getFileName.toString
    val dotIdx = fileName.lastIndexOf('.')
    val stem = if dotIdx > 0 then fileName.substring(0, dotIdx) else fileName
    path.resolveSibling(s"${stem}_executed.md").toString

  private def printUsage(): Unit =
    println(
      """
        |weave-lsp - Polyglot Markdown Notebook Processor with Unix Data Piping & LSP
        |
        |Usage:
        |  scala-cli run . -- <input_notebook.md> [-o output_notebook.md] [-c languages.json] [--no-lsp]
        |
        |Options:
        |  -o, --output <file>  Specify output Markdown file (default: <input>_executed.md)
        |  -c, --config <file>  Path to custom languages.json config file
        |  --no-lsp             Disable Language Server Protocol (LSP) integrations
        |  -h, --help           Show this help message
      """.stripMargin
    )
