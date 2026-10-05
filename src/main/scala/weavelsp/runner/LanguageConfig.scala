package weavelsp.runner

import java.nio.file.{Files, Paths}

case class LangSpec(
  lspCommand: List[String],
  runnerCommand: List[String],
  sentinelTemplate: String,
  fileExtension: String,
  inputBufferTemplate: Option[String] = None,
  replayPreviousCells: Boolean = true,
  timeoutMillis: Long = 120000
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

  val DefaultConfigFile: String = "languages.json"
  val ResourceConfigFile: String = "/default-languages.json"

  def parseJsonConfig(jsonStr: String): Map[String, LangSpec] =
    val json = ujson.read(jsonStr)
    val langsObj = json("languages").obj

    langsObj.map { case (langName, specVal) =>
      val obj = specVal.obj
      val lspCmd = obj.get("lspCommand").map(_.arr.map(_.str).toList).getOrElse(Nil)
      val runnerCmd = obj.get("runnerCommand").map(_.arr.map(_.str).toList).getOrElse(Nil)
      require(runnerCmd.nonEmpty, s"Language '$langName' needs a runnerCommand")
      val sentinel = obj.get("sentinelTemplate").map(_.str).getOrElse("echo \"%s\"")
      val ext = obj.get("fileExtension").map(_.str).getOrElse(".txt")
      val bufTemplate = obj.get("inputBufferTemplate").map(_.str)
      val replay = obj.get("replayPreviousCells").map(_.bool)
        .getOrElse(!Set("idris", "idris2", "idr").contains(langName.toLowerCase))
      val timeout = obj.get("timeoutMillis").map(_.num.toLong).getOrElse(120000L)
      require(timeout > 0, s"Language '$langName' needs a positive timeoutMillis")

      val lower = langName.toLowerCase
      lower -> LangSpec(lspCmd, runnerCmd, sentinel, ext, bufTemplate, replay, timeout)
    }.toMap

  def loadDefaultSpecs(): Map[String, LangSpec] =
    val defaultPath = Paths.get(DefaultConfigFile)
    if Files.exists(defaultPath) then
      parseJsonConfig(Files.readString(defaultPath))
    else
      val stream = getClass.getResourceAsStream(ResourceConfigFile)
      if stream != null then
        try
          val jsonStr = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
          parseJsonConfig(jsonStr)
        finally
          stream.close()
      else
        Map.empty[String, LangSpec]

  def defaultRegistry: LanguageRegistry =
    new LanguageRegistry(loadDefaultSpecs())

  def loadFromFile(path: String, allowMissing: Boolean = false): LanguageRegistry =
    val configPath = Paths.get(path)
    if Files.exists(configPath) then
      try
        val jsonStr = Files.readString(configPath)
        val userSpecs = parseJsonConfig(jsonStr)
        val baseSpecs = loadDefaultSpecs()
        new LanguageRegistry(baseSpecs ++ userSpecs)
      catch
        case scala.util.control.NonFatal(ex) =>
          throw new IllegalArgumentException(s"Invalid language configuration '$path': ${ex.getMessage}", ex)
    else if allowMissing then
      new LanguageRegistry(loadDefaultSpecs())
    else
      throw new IllegalArgumentException(s"Language configuration not found: $path")
