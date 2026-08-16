package weavelsp.parser

import weavelsp.model.{BlockAttributes, ContentType, Visibility}

object BlockHeaderParser:

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
    }

    (positional, kvPairs.toMap)

  private def extractContentType(kvMap: Map[String, String]): ContentType =
    kvMap.get("type")
      .orElse(kvMap.get("format"))
      .orElse(kvMap.get("content-type"))
      .map(ContentType.parse)
      .getOrElse(ContentType.PlainText)

  def parseCodeBlockHeader(headerText: String): BlockAttributes =
    val (firstToken, kvMap) = parseTokens(headerText)

    val name = kvMap.get("name").orElse(kvMap.get("id"))
    val lang = kvMap.get("lang").orElse(kvMap.get("language")).orElse(firstToken)
    val inputs = kvMap.get("input").orElse(kvMap.get("in")).orElse(kvMap.get("inputs"))
      .map(_.split(",").map(_.trim).filter(_.nonEmpty).toList)
      .getOrElse(Nil)

    val contentType = extractContentType(kvMap)
    val codeVis = kvMap.get("code").map(Visibility.parse).getOrElse(Visibility.Visible)
    val outputVis = kvMap.get("output").orElse(kvMap.get("out")).map(Visibility.parse).getOrElse(Visibility.Visible)

    BlockAttributes(
      name = name,
      lang = lang,
      inputs = inputs,
      contentType = contentType,
      codeVisibility = codeVis,
      outputVisibility = outputVis,
      outputFile = kvMap.get("file")
    )

  def parseRenderHeader(headerText: String): Option[(String, ContentType, String)] =
    val (firstToken, kvMap) = parseTokens(headerText)

    val bufName = kvMap.get("render").orElse(kvMap.get("display")).orElse(firstToken)
    val contentType = extractContentType(kvMap)
    val format = kvMap.getOrElse("format", kvMap.getOrElse("style", "pretty"))

    bufName.map(name => (name, contentType, format))
