package weavelsp.runner

import java.nio.file.{Files, Paths}
import scala.util.Try

case class LangSpec(
  lspCommand: List[String],
  runnerCommand: List[String],
  sentinelTemplate: String,
  fileExtension: String,
  inputBufferTemplate: Option[String] = None
)

class LanguageRegistry(val specs: Map[String, LangSpec]):

  private val aliasMap: Map[String, String] = Map(
    "clj"    -> "clojure",
    "sh"     -> "bash",
    "js"     -> "node",
    "cl"     -> "lisp",
    "idr"    -> "idris",
    "idris2" -> "idris"
  )

  def canonicalName(lang: String): String =
    val lower = lang.toLowerCase
    aliasMap.getOrElse(lower, lower)

  def getSpec(language: String): Option[LangSpec] =
    specs.get(canonicalName(language))

object LanguageRegistry:

  def loadFromFile(path: String): LanguageRegistry =
    val configPath = Paths.get(path)
    if Files.exists(configPath) then
      Try {
        val jsonStr = Files.readString(configPath)
        val json = ujson.read(jsonStr)
        val langsObj = json("languages").obj

        val specs = langsObj.map { case (langName, specVal) =>
          val obj = specVal.obj
          val lspCmd = obj.get("lspCommand").map(_.arr.map(_.str).toList).getOrElse(Nil)
          val runnerCmd = obj.get("runnerCommand").map(_.arr.map(_.str).toList).getOrElse(Nil)
          val sentinel = obj.get("sentinelTemplate").map(_.str).getOrElse("echo \"%s\"")
          val ext = obj.get("fileExtension").map(_.str).getOrElse(".txt")
          val bufTemplate = obj.get("inputBufferTemplate").map(_.str)

          langName.toLowerCase -> LangSpec(lspCmd, runnerCmd, sentinel, ext, bufTemplate)
        }.toMap

        new LanguageRegistry(specs)
      }.getOrElse(defaultRegistry)
    else
      defaultRegistry

  def defaultRegistry: LanguageRegistry =
    val defaults = Map(
      "scala" -> LangSpec(
        List("metals"),
        List("scala-cli", "run", "-q"),
        "println(\"%s\")",
        ".sc"
      ),
      "python" -> LangSpec(
        List("pyright-langserver", "--stdio"),
        List("python3", "-u", "-i", "-q"),
        "print(\"%s\", flush=True)",
        ".py"
      ),
      "bash" -> LangSpec(
        List("bash-language-server", "start"),
        List("bash"),
        "echo \"%s\"",
        ".sh"
      ),
      "node" -> LangSpec(
        List("typescript-language-server", "--stdio"),
        List("node", "-i"),
        "console.log(\"%s\")",
        ".js"
      ),
      "clojure" -> LangSpec(
        List("clojure-lsp"),
        List("clojure", "-M"),
        "(do (println \"%s\") (flush))",
        ".clj",
        Some("(System/setProperty \"WEAVE_INPUT\" (String. (.decode (java.util.Base64/getDecoder) (.getBytes \"%s\" \"UTF-8\")) \"UTF-8\"))")
      ),
      "lisp" -> LangSpec(
        List("cl-lsp"),
        List("sbcl", "--noinform", "--script"),
        "(progn (format t \"~a~%%\" \"%s\") (finish-output))",
        ".lisp"
      ),
      "idris" -> LangSpec(
        List("idris2-lsp"),
        List("/home/kaygun/local/bin/idris2", "--source-dir", "/", "--exec", "main"),
        "putStrLn \"%s\"",
        ".idr"
      )
    )
    new LanguageRegistry(defaults)
