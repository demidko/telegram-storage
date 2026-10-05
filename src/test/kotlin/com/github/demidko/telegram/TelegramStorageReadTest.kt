package com.github.demidko.telegram

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.ChatId
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import okhttp3.mockwebserver.MockResponse
import kotlinx.serialization.SerializationException
import kotlinx.serialization.cbor.Cbor
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TelegramStorageReadTest {
  @Serializable
  data class Value(var text: String)

  private fun bytes(text: String) = Cbor.encodeToByteArray(Value.serializer(), Value(text))

  @Test
  fun `default factory preserves metadata calls and observes metadata errors on repeated reads`() =
    FileApiFixture(mapOf("key" to "value")).use { api ->
      api.put("value", bytes("original"))
      TelegramStorage.Constructors.TelegramStorage<String, Value>(api.client(), ChatId.fromId(1)).use { storage ->
        api.requests.clear()
        assertEquals(Value("original"), storage["key"])
        assertEquals(Value("original"), storage["key"])
        assertEquals(2, api.count("metadata:value"))
        api.metadataResponse = { MockResponse().setResponseCode(500) }
        assertNull(storage["key"])
        assertEquals(3, api.count("metadata:value"))
        assertEquals(2, api.count("download:value.cbor"))
      }
    }

  @Test
  fun `explicit factory opt in reuses metadata during a metadata-only outage`() =
    FileApiFixture(mapOf("key" to "value")).use { api ->
      api.put("value", bytes("original"))
      TelegramStorage.Constructors.TelegramStorage<String, Value>(
        api.client(), ChatId.fromId(1), cacheDownloadPaths = true,
      ).use { storage ->
        api.requests.clear()
        assertEquals(Value("original"), storage["key"])
        api.metadataResponse = { MockResponse().setResponseCode(500) }
        assertEquals(Value("original"), storage["key"])
        assertEquals(1, api.count("metadata:value"))
        assertEquals(2, api.count("download:value.cbor"))
      }
    }

  @Test
  fun `original constructor and convenience factory JVM signatures remain available`() =
    FileApiFixture(mapOf("key" to "value")).use { api ->
      api.put("value", bytes("original"))
      val originalConstructor = TelegramStorage::class.java.getConstructor(
        Bot::class.java, ChatId::class.java, KSerializer::class.java, KSerializer::class.java,
      )
      @Suppress("UNCHECKED_CAST")
      val storage = originalConstructor.newInstance(api.client(), ChatId.fromId(1), String.serializer(), Value.serializer())
        as TelegramStorage<String, Value>
      storage.use {
        assertNotNull(it["key"])
        assertNotNull(it["key"])
        assertEquals(2, api.count("metadata:value"))
      }
      val factories = TelegramStorage.Constructors::class.java
      assertNotNull(factories.getDeclaredMethod("TelegramStorage", String::class.java, String::class.java))
      assertNotNull(factories.getDeclaredMethod("TelegramStorage", String::class.java, Long::class.javaPrimitiveType))
      assertNotNull(factories.getDeclaredMethod("TelegramStorage", Bot::class.java, ChatId::class.java))
    }

  @Test
  fun `each read downloads and decodes a fresh mutable value`() = FileApiFixture(mapOf("key" to "value")).use { api ->
    api.put("value", bytes("original"))
    api.open(Value.serializer(), cacheDownloadPaths = true).use { storage ->
      api.requests.clear()
      storage["key"]!!.text = "mutated by caller"
      assertEquals(Value("original"), storage["key"])
      api.put("value", bytes("new response"))
      assertEquals(Value("new response"), storage["key"])
      assertEquals(1, api.count("metadata:value"))
      assertEquals(3, api.count("download:value.cbor"))
    }
    assertEquals(1, api.uploads.get())
    assertEquals(1, api.descriptionUpdates.get())
  }

  @Test
  fun `replacement uses the new file ID instead of the cached old path`() = FileApiFixture(mapOf("key" to "value")).use { api ->
    api.put("value", bytes("old"))
    api.open(Value.serializer(), cacheDownloadPaths = true).use { storage ->
      assertEquals(Value("old"), storage["key"])
      storage["key"] = Value("replacement")
      assertEquals(Value("replacement"), storage["key"])
      assertEquals(1, api.count("metadata:value"))
      assertEquals(1, api.count("metadata:uploaded-1"))
    }
    assertEquals(2, api.uploads.get())
    assertEquals(1, api.descriptionUpdates.get())
  }

  @Test
  fun `missing and removed keys never consult cached file paths`() =
    FileApiFixture(mapOf("first" to "one", "second" to "two")).use { api ->
      api.put("one", bytes("one"))
      api.put("two", bytes("two"))
      api.open(Value.serializer(), cacheDownloadPaths = true).use { storage ->
        assertNotNull(storage["first"])
        assertNotNull(storage["second"])
        storage.remove("first")
        storage.keys.remove("second")
        api.requests.clear()
        assertNull(storage["missing"])
        assertNull(storage["first"])
        assertNull(storage["second"])
        assertTrue(api.requests.isEmpty())
        storage.clear()
        assertNull(storage["first"])
        assertTrue(api.requests.isEmpty())
      }
    }

  @Test
  fun `invalid content still raises a decoding error without retrying metadata`() =
    FileApiFixture(mapOf("key" to "value")).use { api ->
      api.put("value", bytes("valid"))
      api.open(Value.serializer(), cacheDownloadPaths = true).use { storage ->
        assertNotNull(storage["key"])
        api.put("value", byteArrayOf(0xff.toByte()))
        assertThrows(SerializationException::class.java) { storage["key"] }
        assertEquals(1, api.count("metadata:value"))
        assertEquals(2, api.count("download:value.cbor"))
      }
    }
}
