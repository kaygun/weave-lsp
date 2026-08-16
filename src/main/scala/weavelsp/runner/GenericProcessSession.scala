package weavelsp.runner

import weavelsp.model.DataBuffer
import java.nio.file.{Files, Path, Paths, StandardOpenOption}
import java.util.Base64
import scala.util.Try

class GenericProcessSession(
  val langName: String,
  val spec: LangSpec,
  val registry: LanguageRegistry
):

  private val canonicalLang = registry.canonicalName(langName)
  private val sessionFile: Path = Files.createTempFile(s"weave_session_${canonicalLang}_", spec.fileExtension)
  private var lastInputBuffer: Option[DataBuffer] = None
  private var lastLineCount: Int = 0

  def start(): Unit = ()

  def eval(code: String, inputBufferOpt: Option[DataBuffer]): Either[String, String] =
    Try {
      if inputBufferOpt.isDefined then
        lastInputBuffer = inputBufferOpt

      val activeBuffer = inputBufferOpt.orElse(lastInputBuffer)
      val preparedCode = prepareCode(code, activeBuffer)

      // Append cell code directly to cumulative session file
      Files.writeString(sessionFile, preparedCode + "\n\n", StandardOpenOption.APPEND)

      val cmd = if spec.runnerCommand.nonEmpty then
        spec.runnerCommand :+ sessionFile.toString
      else
        List(canonicalLang, sessionFile.toString)

      val pb = new java.lang.ProcessBuilder(cmd*)
      pb.redirectErrorStream(true)

      activeBuffer.foreach { buf =>
        pb.environment().put("WEAVE_INPUT", buf.content)
      }

      val proc = pb.start()
      
      // Close process stdin immediately so script runners don't block
      Try(proc.getOutputStream.close())

      val stdout = new String(proc.getInputStream.readAllBytes(), "UTF-8")
      proc.waitFor()

      val rawLines = stdout.split("\r?\n")
      val contentLines = rawLines.filterNot(isNoiseLine)

      val newLines = contentLines.drop(lastLineCount)
      lastLineCount = contentLines.length

      newLines.mkString("\n").trim
    }.toEither.left.map(_.getMessage)

  private def prepareCode(code: String, activeBuffer: Option[DataBuffer]): String =
    activeBuffer.flatMap { buf =>
      spec.inputBufferTemplate.map { template =>
        val b64Buf = Base64.getEncoder.encodeToString(buf.content.getBytes("UTF-8"))
        val preamble = String.format(template, b64Buf)
        s"$preamble\n$code"
      }
    }.getOrElse(code)

  private def isNoiseLine(line: String): Boolean =
    val t = line.trim
    t.startsWith(">>>") || t.startsWith("user=>") || t.isEmpty

  def close(): Unit =
    Try(Files.deleteIfExists(sessionFile))
