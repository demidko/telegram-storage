package com.github.demidko.telegram

import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.entities.ChatId
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.cbor.Cbor
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import okio.ByteString.Companion.encodeUtf8
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/** Local Telegram-compatible file service. No artificial response delays by default. */
internal class FileApiFixture(initialIndex: Map<String, String> = emptyMap()) : AutoCloseable {
  val paths = ConcurrentHashMap<String, String>()
  val contents = ConcurrentHashMap<String, ByteArray>()
  val requests = CopyOnWriteArrayList<String>()
  val uploads = AtomicInteger()
  val descriptionUpdates = AtomicInteger()
  @Volatile var metadataResponse: ((String) -> MockResponse)? = null
  @Volatile var downloadResponse: ((String) -> MockResponse)? = null
  private val server = MockWebServer()

  init {
    put("index", Cbor.encodeToByteArray(MapSerializer(String.serializer(), String.serializer()), initialIndex))
    server.dispatcher = object : Dispatcher() {
      override fun dispatch(request: RecordedRequest): MockResponse {
        val url = request.requestUrl!!
        return when (url.encodedPath) {
          "/botlocal/getChat" -> json("""{"id":1,"type":"channel","description":"index"}""")
          "/botlocal/getFile" -> {
            val id = url.queryParameter("file_id")!!
            requests += "metadata:$id"
            metadataResponse?.invoke(id) ?: metadata(id)
          }
          "/botlocal/sendDocument" -> {
            val id = "uploaded-${uploads.incrementAndGet()}"
            put(id, uploadedBytes(request))
            json("""{"message_id":1,"date":0,"chat":{"id":1,"type":"channel"},"document":{"file_id":"$id","file_unique_id":"$id"}}""")
          }
          "/botlocal/setChatDescription" -> {
            descriptionUpdates.incrementAndGet()
            json("true")
          }
          else -> {
            val path = url.encodedPath.removePrefix("/file/botlocal/")
            requests += "download:$path"
            downloadResponse?.invoke(path) ?: download(path)
          }
        }
      }
    }
    server.start()
  }

  fun client() = bot { token = "local"; apiUrl = server.url("/").toString() }

  fun <V> open(valueSerializer: KSerializer<V>, cacheDownloadPaths: Boolean = false) =
    TelegramStorage(client(), ChatId.fromId(1), String.serializer(), valueSerializer, cacheDownloadPaths)

  fun put(id: String, bytes: ByteArray, path: String = "$id.cbor") {
    paths[id] = path
    contents[path] = bytes
  }

  fun metadata(id: String): MockResponse {
    val path = paths[id] ?: return MockResponse().setResponseCode(404)
    return json("""{"file_id":"$id","file_unique_id":"$id","file_path":"$path"}""")
  }

  fun download(path: String): MockResponse = contents[path]?.let { MockResponse().setBody(Buffer().write(it)) }
    ?: MockResponse().setResponseCode(404)

  fun count(prefix: String) = requests.count { it.startsWith(prefix) }

  private fun json(result: String) = MockResponse().setHeader("Content-Type", "application/json")
    .setBody("""{"ok":true,"result":$result}""")

  private fun uploadedBytes(request: RecordedRequest): ByteArray {
    val body = request.body.clone().readByteString()
    val boundary = request.getHeader("Content-Type")!!.substringAfter("boundary=")
    val header = body.indexOf("name=\"document\"".encodeUtf8())
    check(header >= 0)
    val start = body.indexOf("\r\n\r\n".encodeUtf8(), header) + 4
    return body.substring(start, body.indexOf("\r\n--$boundary".encodeUtf8(), start)).toByteArray()
  }

  override fun close() = server.shutdown()
}
