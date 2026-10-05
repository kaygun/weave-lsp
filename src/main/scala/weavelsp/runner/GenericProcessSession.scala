package weavelsp.runner

import weavelsp.model.DataBuffer
import java.nio.file.{Files, Path}
import java.util.{Base64, UUID}
import java.util.concurrent.TimeUnit
import scala.jdk.CollectionConverters.*
import scala.util.Try

/** Reconstructs language state by replaying successful cells in a script. */
class GenericProcessSession(
  val langName: String,
  val spec: LangSpec,
  val registry: LanguageRegistry,
  val workingDir: Option[Path] = None
):

  private val canonicalLang = registry.canonicalName(langName)
  private val sessionFile: Path = Files.createTempFile(s"weave_session_${canonicalLang}_", spec.fileExtension)
  private var history = ""
  private var previousInput: Option[DataBuffer] = None

  def start(): Unit = ()

  def eval(code: String, inputBufferOpt: Option[DataBuffer], replayCell: Boolean = true): Either[String, String] =
    Try {
      // Some runtimes cannot change their environment from within a script. Do
      // not silently replay their previous cells against a different input.
      require(history.isEmpty || inputPreamble(inputBufferOpt).isDefined ||
        previousInput.map(_.content) == inputBufferOpt.map(_.content),
        s"$canonicalLang requires an inputBufferTemplate to replay cells with different inputs")

      // Compile Python cells separately so valid future imports remain at the
      // beginning of their compilation unit despite the input preamble.
      val executableCode = if canonicalLang == "python" then
        s"exec(compile(${ujson.write(code)}, ${ujson.write(sessionFile.toString)}, 'exec'), globals(), globals())"
      else code
      val preparedCode = inputPreamble(inputBufferOpt).getOrElse("") + executableCode + "\n\n"
      val marker = s"__WEAVE_OUTPUT_${UUID.randomUUID().toString.replace("-", "")}__"
      val source = if history.isEmpty then preparedCode
        else history + String.format(spec.sentinelTemplate, marker) + "\n" + preparedCode
      Files.writeString(sessionFile, source)

      val resolvedRunner = spec.runnerCommand match
        case head :: tail if !head.contains("/") =>
          val home = System.getProperty("user.home")
          val candidateDirs = (Option(System.getenv("PATH")).getOrElse("").split(":").toList ++ List(s"$home/local/bin", s"$home/.local/bin")).distinct
          val found = candidateDirs.map(d => java.nio.file.Paths.get(d, head)).find(p => java.nio.file.Files.isExecutable(p))
          found.map(_.toString).getOrElse(head) :: tail
        case other => other
      val cmd = resolvedRunner :+ sessionFile.toString
      val stdoutFile = Files.createTempFile("weave-stdout-", ".txt")
      val stderrFile = Files.createTempFile("weave-stderr-", ".txt")
      var process: Option[Process] = None
      try
        val pb = new java.lang.ProcessBuilder(cmd*)
        workingDir.foreach(dir => pb.directory(dir.toFile))
        val home = System.getProperty("user.home")
        val extraBins = List(s"$home/local/bin", s"$home/.local/bin")
        val currentPath = Option(pb.environment().get("PATH")).getOrElse("")
        val pathEntries = currentPath.split(":").toSet
        val toAdd = extraBins.filter(b => java.nio.file.Files.isDirectory(java.nio.file.Paths.get(b)) && !pathEntries.contains(b))
        if toAdd.nonEmpty then
          pb.environment().put("PATH", (toAdd :+ currentPath).mkString(":"))
        pb.redirectOutput(stdoutFile.toFile)
        pb.redirectError(stderrFile.toFile)
        pb.environment().remove("WEAVE_INPUT")
        inputBufferOpt.foreach(buf => pb.environment().put("WEAVE_INPUT", buf.content))
        val proc = pb.start()
        process = Some(proc)
        proc.getOutputStream.close()
        if !proc.waitFor(spec.timeoutMillis, TimeUnit.MILLISECONDS) then
          throw new IllegalStateException(s"$canonicalLang execution timed out after ${spec.timeoutMillis} ms")

        val stdout = Files.readString(stdoutFile)
        val stderr = Files.readString(stderrFile)
        if proc.exitValue() != 0 then
          val detail = if stderr.nonEmpty then stderr else stdout
          throw new IllegalStateException(s"$canonicalLang exited with code ${proc.exitValue()}: ${detail.trim}")
        if stderr.nonEmpty then Console.err.print(stderr)

        val output = if history.isEmpty then stdout else
          val boundary = stdout.indexOf(marker)
          require(boundary >= 0, s"$canonicalLang did not emit the cell output boundary")
          stdout.substring(boundary + marker.length).stripPrefix("\r\n").stripPrefix("\n")

        if spec.replayPreviousCells && replayCell then history += preparedCode
        previousInput = inputBufferOpt
        output
      finally
        process.filter(_.isAlive).foreach { proc =>
          val descendants = proc.descendants()
          try descendants.iterator().asScala.toList.reverse.foreach(_.destroyForcibly())
          finally descendants.close()
          proc.destroyForcibly()
          proc.waitFor(1, TimeUnit.SECONDS)
        }
        Files.deleteIfExists(stdoutFile)
        Files.deleteIfExists(stderrFile)
    }.toEither.left.map(ex => Option(ex.getMessage).getOrElse(ex.toString))

  /** Bind the input separately for every cell, including replayed cells. */
  private def inputPreamble(input: Option[DataBuffer]): Option[String] =
    val content = input.map(_.content)
    val literal = ujson.write(content.getOrElse(""))
    val builtIn = canonicalLang match
      case "python" =>
        Some("import os as __weave_os\n" + content.fold(
          "__weave_os.environ.pop('WEAVE_INPUT', None)\n")(
          _ => s"__weave_os.environ['WEAVE_INPUT'] = $literal\n"))
      case "bash" =>
        Some(content.fold("unset WEAVE_INPUT\n")(value =>
          "export WEAVE_INPUT='" + value.replace("'", "'\"'\"'") + "'\n"))
      case "node" =>
        Some(content.fold("delete process.env.WEAVE_INPUT;\n")(
          _ => s"process.env.WEAVE_INPUT = $literal;\n"))
      case "r" =>
        Some(content.fold("Sys.unsetenv('WEAVE_INPUT')\n")(
          _ => s"Sys.setenv(WEAVE_INPUT = $literal)\n"))
      case "clojure" =>
        Some(content.fold("(System/clearProperty \"WEAVE_INPUT\")\n")(
          _ => s"(System/setProperty \"WEAVE_INPUT\" $literal)\n"))
      case "lisp" =>
        val binding = content.fold("(sb-posix:unsetenv \"WEAVE_INPUT\")\n") { value =>
          val escaped = value.replace("\\", "\\\\").replace("\"", "\\\"")
          s"(sb-posix:setenv \"WEAVE_INPUT\" \"$escaped\" 1)\n"
        }
        Some("(require :sb-posix)\n" + binding)
      case _ => None

    val configured = spec.inputBufferTemplate.flatMap { template =>
      content.map { text =>
        val encoded = Base64.getEncoder.encodeToString(text.getBytes(java.nio.charset.StandardCharsets.UTF_8))
        String.format(template, encoded) + "\n"
      }
    }
    if builtIn.isDefined || configured.isDefined then Some(builtIn.getOrElse("") + configured.getOrElse(""))
    else None

  def close(): Unit =
    Files.deleteIfExists(sessionFile)
