package com.github.demidko.telegram

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.cbor.Cbor
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TelegramStorageReadTest {
  @Serializable
  data class Value(var text: String)

  private fun bytes(text: String) = Cbor.encodeToByteArray(Value.serializer(), Value(text))

  @Test
  fun `each read downloads and decodes a fresh mutable value`() = FileApiFixture(mapOf("key" to "value")).use { api ->
    api.put("value", bytes("original"))
    api.open(Value.serializer()).use { storage ->
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
    api.open(Value.serializer()).use { storage ->
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
      api.open(Value.serializer()).use { storage ->
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
      api.open(Value.serializer()).use { storage ->
        assertNotNull(storage["key"])
        api.put("value", byteArrayOf(0xff.toByte()))
        assertThrows(SerializationException::class.java) { storage["key"] }
        assertEquals(1, api.count("metadata:value"))
        assertEquals(2, api.count("download:value.cbor"))
      }
    }
}
