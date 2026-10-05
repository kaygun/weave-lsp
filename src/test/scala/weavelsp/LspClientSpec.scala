package weavelsp

import munit.FunSuite
import weavelsp.lsp.LspClient
import java.nio.file.Files

class LspClientSpec extends FunSuite:
  private val protocol = """import sys, json, time
    |def read():
    |    length = 0
    |    while True:
    |        line = sys.stdin.buffer.readline()
    |        if line in (b'\r\n', b'\n', b''):
    |            break
    |        if line.lower().startswith(b'content-length:'):
    |            length = int(line.split(b':')[1])
    |    return json.loads(sys.stdin.buffer.read(length)) if length else {}
    |def send(message):
    |    body = json.dumps(message, ensure_ascii=False).encode('utf-8')
    |    sys.stdout.buffer.write(('Content-Length: %d\r\n\r\n' % len(body)).encode('ascii') + body)
    |    sys.stdout.buffer.flush()
    |""".stripMargin

  private def withServer(body: String, timeout: Long = 500)(f: LspClient => Unit): Unit =
    val script = Files.createTempFile("weave-fake-lsp-", ".py")
    Files.writeString(script, protocol + body)
    val client = new LspClient(List("python3", "-u", script.toString), timeout)
    try f(client)
    finally
      client.shutdown()
      Files.deleteIfExists(script)

  test("notifications and unrelated response ids cannot mask failed initialization") {
    withServer("""request = read()
      |send({'jsonrpc':'2.0', 'method':'window/logMessage', 'params':{'message':'héllo'}})
      |send({'jsonrpc':'2.0', 'id':999, 'result':{}})
      |send({'jsonrpc':'2.0', 'id':request['id'], 'error':{'code':-32603, 'message':'failed'}})
      |time.sleep(10)
      |""".stripMargin) { client =>
      assertEquals(client.start(), false)
    }
  }

  test("server requests receive a response while initialization is pending") {
    withServer("""request = read()
      |send({'jsonrpc':'2.0', 'id':'server-1', 'method':'unsupported/request', 'params':{}})
      |answer = read()
      |assert answer['id'] == 'server-1' and answer['error']['code'] == -32601
      |send({'jsonrpc':'2.0', 'id':request['id'], 'result':{'capabilities':{}}})
      |assert read()['method'] == 'initialized'
      |request = read()
      |send({'jsonrpc':'2.0', 'id':request['id'], 'result':None})
      |read()
      |""".stripMargin) { client =>
      assertEquals(client.start(), true)
    }
  }

  test("silent server startup times out") {
    withServer("time.sleep(10)\n", timeout = 150) { client =>
      val start = System.nanoTime()
      assertEquals(client.start(), false)
      assert((System.nanoTime() - start) / 1000000 < 2000)
    }
  }

  test("unresponsive shutdown is bounded and server stderr cannot block startup") {
    withServer("""request = read()
      |sys.stderr.write('x' * 200000)
      |sys.stderr.flush()
      |send({'jsonrpc':'2.0', 'id':request['id'], 'result':{'capabilities':{}}})
      |time.sleep(10)
      |""".stripMargin, timeout = 200) { client =>
      assertEquals(client.start(), true)
      val start = System.nanoTime()
      client.shutdown()
      assert((System.nanoTime() - start) / 1000000 < 2000)
    }
  }
