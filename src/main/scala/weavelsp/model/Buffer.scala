package weavelsp.model

import scala.xml.XML
import scala.collection.concurrent.TrieMap
import scala.util.Try

enum ContentType:
  case PlainText, Json, Xml

  def format(raw: String): String =
    if raw.isBlank then ""
    else
      this match
        case ContentType.Json =>
          Try(ujson.write(ujson.read(raw), indent = 2)).getOrElse(raw)
        case ContentType.Xml =>
          Try {
            val node = XML.loadString(raw)
            val p = new scala.xml.PrettyPrinter(80, 2)
            p.format(node)
          }.getOrElse(raw)
        case ContentType.PlainText =>
          raw

object ContentType:
  def parse(str: String): ContentType = str.toLowerCase match
    case "json" => ContentType.Json
    case "xml"  => ContentType.Xml
    case _      => ContentType.PlainText

case class DataBuffer(
  name: String,
  content: String,
  contentType: ContentType,
  timestamp: Long = System.currentTimeMillis()
)

class BufferStore:
  private val buffers = TrieMap[String, DataBuffer]()

  def put(buffer: DataBuffer): Unit =
    buffers.put(buffer.name, buffer)

  def get(name: String): Option[DataBuffer] =
    buffers.get(name)

  def getAll: Map[String, DataBuffer] =
    buffers.toMap

  def clear(): Unit =
    buffers.clear()
