package weavelsp.lsp

import java.io.{InputStream, OutputStream}
import java.nio.charset.StandardCharsets
import java.util.concurrent.{LinkedBlockingQueue, TimeUnit}
import scala.jdk.CollectionConverters.*
import scala.util.Try

class LspClient(val lspCommand: List[String], val requestTimeoutMillis: Long = 3000):

  private var processOpt: Option[Process] = None
  private var inStream: InputStream = null
  private var outStream: OutputStream = null
  private var requestId = 0
  private val responses = new LinkedBlockingQueue[ujson.Value]()

  def start(): Boolean =
    if lspCommand.isEmpty then false
    else
      val started = Try {
        val pb = new java.lang.ProcessBuilder(lspCommand*)
        pb.redirectError(java.lang.ProcessBuilder.Redirect.DISCARD)
        val proc = pb.start()
        processOpt = Some(proc)
        inStream = proc.getInputStream
        outStream = proc.getOutputStream
        val reader = new Thread(() => readMessages(), "weave-lsp-reader")
        reader.setDaemon(true)
        reader.start()
        initialize()
      }.getOrElse(false)
      if !started then stopProcess()
      started

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
    this.synchronized {
      val contentBytes = ujson.write(json).getBytes(StandardCharsets.UTF_8)
      val header = s"Content-Length: ${contentBytes.length}\r\n\r\n".getBytes(StandardCharsets.UTF_8)
      outStream.write(header)
      outStream.write(contentBytes)
      outStream.flush()
    }

  private def readResponse(expectedId: Int): ujson.Value =
    val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(requestTimeoutMillis)
    var remaining = deadline - System.nanoTime()
    while remaining > 0 do
      val response = responses.poll(remaining, TimeUnit.NANOSECONDS)
      if response == null || response == ujson.Null then return ujson.Null
      if response.obj.get("id").contains(ujson.Num(expectedId)) then return response
      remaining = deadline - System.nanoTime()
    ujson.Null

  private def readMessages(): Unit =
    try
      var message = readMessage()
      while message != ujson.Null do
        if message.obj.contains("id") then
          if message.obj.contains("method") then
            // Answer unsupported server requests so initialization cannot deadlock.
            writeJsonRpc(ujson.Obj("jsonrpc" -> "2.0", "id" -> message("id"),
              "error" -> ujson.Obj("code" -> -32601, "message" -> "Method not supported")))
          else responses.offer(message)
        // Drain notifications even when no request is pending.
        message = readMessage()
    catch
      case scala.util.control.NonFatal(_) => ()
    finally responses.offer(ujson.Null)

  private def readMessage(): ujson.Value =
    var contentLength = 0
    var line = readHeaderLine()
    while (line != null && line.trim.nonEmpty) {
      if line.toLowerCase.startsWith("content-length:") then
        contentLength = line.split(":")(1).trim.toInt
      line = readHeaderLine()
    }

    if contentLength > 0 then
      val bodyBytes = inStream.readNBytes(contentLength)
      require(bodyBytes.length == contentLength, "Truncated LSP response")
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
    val rootUriStr = java.nio.file.Paths.get(".").toAbsolutePath.toUri.toString
    val initParams = ujson.Obj(
      "processId" -> ujson.Null,
      "rootUri" -> rootUriStr,
      "capabilities" -> ujson.Obj()
    )
    val res = sendRequest("initialize", initParams)
    val success = res != ujson.Null && res.obj.contains("result") && !res.obj.contains("error")
    if success then sendNotification("initialized", ujson.Obj())
    success

  def didOpen(uri: String, languageId: String, text: String): Unit =
    val standardLangId = languageId.toLowerCase match
      case "lisp" => "commonlisp"
      case "node" | "js" => "javascript"
      case other => other
    sendNotification(
      "textDocument/didOpen",
      ujson.Obj(
        "textDocument" -> ujson.Obj(
          "uri" -> uri,
          "languageId" -> standardLangId,
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
    try
      if processOpt.exists(_.isAlive) then
        Try {
          sendRequest("shutdown", ujson.Null)
          sendNotification("exit", ujson.Null)
        }
    finally stopProcess()

  private def stopProcess(): Unit =
    processOpt.foreach { proc =>
      val descendants = proc.descendants()
      try descendants.iterator().asScala.toList.reverse.foreach(_.destroyForcibly())
      finally descendants.close()
      proc.destroyForcibly()
      Try(proc.getOutputStream.close())
      Try(proc.getInputStream.close())
      Try(proc.getErrorStream.close())
      Try(proc.waitFor(1, TimeUnit.SECONDS))
    }
    processOpt = None
