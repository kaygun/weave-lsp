package weavelsp.lsp

import weavelsp.runner.LanguageRegistry
import scala.collection.concurrent.TrieMap
import scala.util.Try

class LspServerManager(val registry: LanguageRegistry):

  private val clients = TrieMap[String, LspClient]()
  private val unavailable = scala.collection.mutable.Set[String]()
  private val versions = scala.collection.mutable.Map[(String, String), Int]()

  def getOrStartClient(language: String): Option[LspClient] =
    val canonicalKey = registry.canonicalName(language)
    if unavailable.contains(canonicalKey) then None
    else clients.get(canonicalKey).orElse {
      val started = registry.getSpec(canonicalKey).filter(_.lspCommand.nonEmpty).flatMap { spec =>
        val client = new LspClient(spec.lspCommand)
        if client.start() then
          clients.put(canonicalKey, client)
          Some(client)
        else None
      }
      if started.isEmpty then unavailable += canonicalKey
      started
    }

  def notifyCellUpdated(language: String, virtualUri: String, code: String): Unit =
    getOrStartClient(language).foreach { client =>
      val canonical = registry.canonicalName(language)
      val key = (canonical, virtualUri)
      val version = versions.getOrElse(key, 0) + 1
      Try {
        if version == 1 then client.didOpen(virtualUri, canonical, code)
        else client.didChange(virtualUri, version, code)
        versions(key) = version
      }
    }

  def shutdownAll(): Unit =
    clients.values.foreach(client => Try(client.shutdown()))
    clients.clear()
    unavailable.clear()
    versions.clear()
