package weavelsp.lsp

import java.io.{InputStream, OutputStream}
import java.nio.charset.StandardCharsets
import scala.util.Try

class LspClient(val lspCommand: List[String]):

  private var processOpt: Option[Process] = None
  private var inStream: InputStream = null
  private var outStream: OutputStream = null
  private var requestId = 0

  def start(): Boolean =
    if lspCommand.isEmpty then false
    else
      Try {
        val pb = new java.lang.ProcessBuilder(lspCommand*)
        val proc = pb.start()
        processOpt = Some(proc)
        inStream = proc.getInputStream
        outStream = proc.getOutputStream
        initialize()
      }.getOrElse(false)

  private def nextId(): Int =
    requestId += 1
    requestId

  private def sendNotification(method: String, params: ujson.Value): Unit =
    if outStream != null then
      writeJsonRpc(createRpcMessage(method = Some(method), params = params))

  private def sendRequest(method: String, params: ujson.Value): ujson.Value =
    if outStream != null then
      val id = nextId()
      writeJsonRpc(createRpcMessage(id = Some(id), method = Some(method), params = params))
      readResponse(id)
    else
      ujson.Null

  private def createRpcMessage(
    id: Option[Int] = None,
    method: Option[String] = None,
    params: ujson.Value = ujson.Null
  ): ujson.Obj =
    val obj = ujson.Obj("jsonrpc" -> "2.0")
    id.foreach(i => obj("id") = i)
    method.foreach(m => obj("method") = m)
    if params != ujson.Null then obj("params") = params
    obj

  private def writeJsonRpc(json: ujson.Value): Unit =
    val contentBytes = ujson.write(json).getBytes(StandardCharsets.UTF_8)
    val header = s"Content-Length: ${contentBytes.length}\r\n\r\n".getBytes(StandardCharsets.UTF_8)
    outStream.write(header)
    outStream.write(contentBytes)
    outStream.flush()

  private def readResponse(expectedId: Int): ujson.Value =
    var contentLength = 0
    var line = readHeaderLine()
    while (line != null && line.trim.nonEmpty) {
      if line.toLowerCase.startsWith("content-length:") then
        contentLength = line.split(":")(1).trim.toInt
      line = readHeaderLine()
    }

    if contentLength > 0 then
      val bodyBytes = inStream.readNBytes(contentLength)
      val bodyStr = new String(bodyBytes, StandardCharsets.UTF_8)
      Try(ujson.read(bodyStr)).getOrElse(ujson.Null)
    else
      ujson.Null

  private def readHeaderLine(): String =
    val sb = new StringBuilder()
    var b = inStream.read()
    while (b != -1 && b != '\n') {
      if b != '\r' then sb.append(b.toChar)
      b = inStream.read()
    }
    if sb.isEmpty && b == -1 then null else sb.toString()

  def initialize(): Boolean =
    val initParams = ujson.Obj(
      "processId" -> ujson.Null,
      "rootUri" -> ujson.Null,
      "capabilities" -> ujson.Obj()
    )
    val res = sendRequest("initialize", initParams)
    sendNotification("initialized", ujson.Obj())
    res != ujson.Null

  def didOpen(uri: String, languageId: String, text: String): Unit =
    sendNotification(
      "textDocument/didOpen",
      ujson.Obj(
        "textDocument" -> ujson.Obj(
          "uri" -> uri,
          "languageId" -> languageId,
          "version" -> 1,
          "text" -> text
        )
      )
    )

  def didChange(uri: String, version: Int, text: String): Unit =
    sendNotification(
      "textDocument/didChange",
      ujson.Obj(
        "textDocument" -> ujson.Obj("uri" -> uri, "version" -> version),
        "contentChanges" -> ujson.Arr(ujson.Obj("text" -> text))
      )
    )

  def shutdown(): Unit =
    Try {
      sendRequest("shutdown", ujson.Null)
      sendNotification("exit", ujson.Null)
      processOpt.foreach(_.destroyForcibly())
    }
