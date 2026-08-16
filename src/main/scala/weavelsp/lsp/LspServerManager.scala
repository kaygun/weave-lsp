package weavelsp.lsp

import weavelsp.runner.LanguageRegistry
import scala.collection.concurrent.TrieMap
import scala.util.Try

class LspServerManager(val registry: LanguageRegistry):

  private val clients = TrieMap[String, LspClient]()

  def getOrStartClient(language: String): Option[LspClient] =
    val canonicalKey = registry.canonicalName(language)
    clients.get(canonicalKey).orElse {
      for
        spec   <- registry.getSpec(canonicalKey)
        if spec.lspCommand.nonEmpty
        client = new LspClient(spec.lspCommand)
        if client.start()
      yield
        clients.put(canonicalKey, client)
        client
    }

  def notifyCellUpdated(language: String, virtualUri: String, code: String): Unit =
    getOrStartClient(language).foreach { client =>
      Try(client.didOpen(virtualUri, registry.canonicalName(language), code))
    }

  def shutdownAll(): Unit =
    clients.values.foreach(client => Try(client.shutdown()))
    clients.clear()
