package weavelsp.parser

import weavelsp.model.{BlockAttributes, ContentType, Visibility}

object BlockHeaderParser:

  private val validCodeKeys = Set(
    "name", "id",
    "lang", "language",
    "input", "in", "inputs",
    "type", "format", "content-type",
    "code",
    "output", "out",
    "eval", "run",
    "replay",
    "file"
  )

  private val validRenderKeys = Set(
    "render", "display",
    "type", "content-type",
    "format", "style"
  )

  /**
   * Tokenize header text into positional leading token (if present) and key-value pair map.
   * Example: "python name:cell1 input:cell0 type:json"
   * Yields: positional = Some("python"), kvPairs = Map("name" -> "cell1", "input" -> "cell0", "type" -> "json")
   */
  private def parseTokens(headerText: String): (Option[String], Map[String, String]) =
    val tokens = headerText.trim.split("\\s+").filter(_.nonEmpty).toList
    var positional: Option[String] = None
    val kvPairs = scala.collection.mutable.Map[String, String]()

    tokens.zipWithIndex.foreach { (token, idx) =>
      if token.contains(":") then
        val parts = token.split(":", 2)
        kvPairs(parts(0).toLowerCase) = parts(1)
      else if idx == 0 then
        positional = Some(token.toLowerCase)
      else
        throw new IllegalArgumentException(s"Unexpected positional token '$token' in header '$headerText'")
    }

    (positional, kvPairs.toMap)

  private def parseBoolean(str: String, attr: String): Boolean = str.toLowerCase match
    case "true" | "yes" | "1" => true
    case "false" | "no" | "0" => false
    case other =>
      throw new IllegalArgumentException(s"Invalid boolean value '$other' for '$attr'; expected 'true' or 'false'")

  def parseCodeBlockHeader(headerText: String): BlockAttributes =
    val (firstToken, kvMap) = parseTokens(headerText)

    kvMap.keys.foreach { k =>
      if !validCodeKeys.contains(k) then
        throw new IllegalArgumentException(s"Unknown code block attribute '$k' in header '$headerText'")
    }

    val name = kvMap.get("name").orElse(kvMap.get("id"))
    val lang = kvMap.get("lang").orElse(kvMap.get("language")).orElse(firstToken)
    val inputs = kvMap.get("input").orElse(kvMap.get("in")).orElse(kvMap.get("inputs"))
      .map(_.split(",").map(_.trim).filter(_.nonEmpty).toList)
      .getOrElse(Nil)

    val contentType = kvMap.get("type")
      .orElse(kvMap.get("content-type"))
      .orElse(kvMap.get("format"))
      .map(ContentType.parse)
      .getOrElse(ContentType.PlainText)

    val codeVis = kvMap.get("code").map(Visibility.parse).getOrElse(Visibility.Visible)
    val outputVis = kvMap.get("output").orElse(kvMap.get("out")).map(Visibility.parse).getOrElse(Visibility.Visible)
    val evalOpt = kvMap.get("eval").orElse(kvMap.get("run")).map(v => parseBoolean(v, "eval"))
    val replayOpt = kvMap.get("replay").map(v => parseBoolean(v, "replay"))

    BlockAttributes(
      name = name,
      lang = lang,
      inputs = inputs,
      contentType = contentType,
      codeVisibility = codeVis,
      outputVisibility = outputVis,
      eval = evalOpt,
      replay = replayOpt,
      outputFile = kvMap.get("file")
    )

  def parseRenderHeader(headerText: String): Option[(String, ContentType, String)] =
    val (_, kvMap) = parseTokens(headerText)

    val bufName = kvMap.get("render").orElse(kvMap.get("display")).filter(_.nonEmpty)
    bufName.map { name =>
      kvMap.keys.foreach { k =>
        if !validRenderKeys.contains(k) then
          throw new IllegalArgumentException(s"Unknown render block attribute '$k' in header '$headerText'")
      }

      val contentType = kvMap.get("type")
        .orElse(kvMap.get("content-type"))
        .map(ContentType.parse)
        .getOrElse(ContentType.PlainText)

      val format = kvMap.getOrElse("format", kvMap.getOrElse("style", "pretty")).toLowerCase
      (name, contentType, format)
    }
