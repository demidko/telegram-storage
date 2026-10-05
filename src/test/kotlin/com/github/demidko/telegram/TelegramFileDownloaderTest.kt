package com.github.demidko.telegram

import com.google.common.base.Ticker
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.SocketPolicy
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.MINUTES
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

private class ManualTicker : Ticker() {
  private val nanos = AtomicLong()
  override fun read() = nanos.get()
  fun advance(minutes: Long) { nanos.addAndGet(MINUTES.toNanos(minutes)) }
}

class TelegramFileDownloaderTest {
  @Test
  fun `warm reads reuse metadata but always download fresh bytes`() = FileApiFixture().use { api ->
    api.put("value", byteArrayOf(1))
    val downloader = TelegramFileDownloader(api.client())
    assertArrayEquals(byteArrayOf(1), downloader.download("value"))
    api.put("value", byteArrayOf(2))
    assertArrayEquals(byteArrayOf(2), downloader.download("value"))
    assertEquals(1, api.count("metadata:value"))
    assertEquals(2, api.count("download:value.cbor"))
  }

  @Test
  fun `lifetime expires after thirty minutes and hits do not extend it`() = FileApiFixture().use { api ->
    api.put("value", byteArrayOf(1))
    val ticker = ManualTicker()
    val downloader = TelegramFileDownloader(api.client(), ticker)
    downloader.download("value")
    ticker.advance(29)
    downloader.download("value")
    assertEquals(1, api.count("metadata:value"))
    ticker.advance(1)
    downloader.download("value")
    assertEquals(2, api.count("metadata:value"))
  }

  @Test
  fun `slow metadata request does not grant a new lifetime on arrival`() = FileApiFixture().use { api ->
    api.put("value", byteArrayOf(1))
    val ticker = ManualTicker()
    api.metadataResponse = { id -> ticker.advance(30); api.metadata(id) }
    val downloader = TelegramFileDownloader(api.client(), ticker)
    assertNotNull(downloader.download("value"))
    assertNotNull(downloader.download("value"))
    assertEquals(2, api.count("metadata:value"))
  }

  @Test
  fun `entry bound evicts paths instead of retaining the entire keyspace`() = FileApiFixture().use { api ->
    val ids = listOf("one", "two", "three")
    ids.forEach { api.put(it, byteArrayOf(1)) }
    val downloader = TelegramFileDownloader(api.client(), maximumSize = 2)
    ids.forEach { assertNotNull(downloader.download(it)) }
    ids.forEach { assertNotNull(downloader.download(it)) }
    assertTrue(api.count("metadata:") > 3, "at least one of three paths must have been evicted")
    assertEquals(6, api.count("download:"))
  }

  @Test
  fun `cached failed path refreshes once and does not return old content`() = FileApiFixture().use { api ->
    api.put("value", byteArrayOf(1), "old")
    val downloader = TelegramFileDownloader(api.client())
    downloader.download("value")
    api.contents.remove("old")
    api.put("value", byteArrayOf(2), "new")
    api.requests.clear()
    assertArrayEquals(byteArrayOf(2), downloader.download("value"))
    assertEquals(listOf("download:old", "metadata:value", "download:new"), api.requests.toList())
    api.requests.clear()
    assertArrayEquals(byteArrayOf(2), downloader.download("value"))
    assertEquals(listOf("download:new"), api.requests.toList())
  }

  @Test
  fun `failure after refresh stops at two downloads and does not cache failure`() = FileApiFixture().use { api ->
    api.put("value", byteArrayOf(1))
    val downloader = TelegramFileDownloader(api.client())
    downloader.download("value")
    api.contents.clear()
    api.requests.clear()
    assertNull(downloader.download("value"))
    assertEquals(listOf("download:value.cbor", "metadata:value", "download:value.cbor"), api.requests.toList())
    api.requests.clear()
    assertNull(downloader.download("value"))
    assertEquals(listOf("metadata:value", "download:value.cbor"), api.requests.toList())
  }

  @Test
  fun `failed metadata is not cached`() = FileApiFixture().use { api ->
    api.put("value", byteArrayOf(1))
    api.metadataResponse = { MockResponse().setResponseCode(500) }
    val downloader = TelegramFileDownloader(api.client())
    assertNull(downloader.download("value"))
    api.metadataResponse = null
    assertNotNull(downloader.download("value"))
    assertEquals(2, api.count("metadata:value"))
    assertEquals(1, api.count("download:"))
  }

  @Test
  fun `missing file path is not cached`() = FileApiFixture().use { api ->
    api.put("value", byteArrayOf(1))
    api.metadataResponse = {
      MockResponse().setBody("""{"ok":true,"result":{"file_id":"value","file_unique_id":"value"}}""")
    }
    val downloader = TelegramFileDownloader(api.client())
    assertNull(downloader.download("value"))
    api.metadataResponse = null
    assertNotNull(downloader.download("value"))
    assertEquals(2, api.count("metadata:value"))
  }

  @Test
  fun `warm valid path remains readable during a metadata-only outage`() = FileApiFixture().use { api ->
    api.put("value", byteArrayOf(1))
    val ticker = ManualTicker()
    val downloader = TelegramFileDownloader(api.client(), ticker)
    downloader.download("value")
    api.metadataResponse = { MockResponse().setResponseCode(500) }
    assertArrayEquals(byteArrayOf(1), downloader.download("value"))
    assertEquals(1, api.count("metadata:value"))
    ticker.advance(30)
    assertNull(downloader.download("value"))
    assertEquals(2, api.count("metadata:value"))
  }

  @Test
  fun `deleted file returns null when cached path and refreshed metadata both fail`() = FileApiFixture().use { api ->
    api.put("value", byteArrayOf(1))
    val downloader = TelegramFileDownloader(api.client())
    downloader.download("value")
    api.paths.remove("value")
    api.contents.remove("value.cbor")
    api.requests.clear()
    assertNull(downloader.download("value"))
    assertEquals(listOf("download:value.cbor", "metadata:value"), api.requests.toList())
  }

  @Test
  fun `cache is not shared across downloaders or bot contexts`() = FileApiFixture().use { first ->
    FileApiFixture().use { second ->
      first.put("same-id", byteArrayOf(1), "first")
      second.put("same-id", byteArrayOf(2), "second")
      assertArrayEquals(byteArrayOf(1), TelegramFileDownloader(first.client()).download("same-id"))
      assertArrayEquals(byteArrayOf(2), TelegramFileDownloader(second.client()).download("same-id"))
      assertNotNull(TelegramFileDownloader(first.client()).download("same-id"))
      assertEquals(2, first.count("metadata:same-id"))
      assertEquals(1, second.count("metadata:same-id"))
    }
  }

  @Test
  fun `warm callers download concurrently without serializing unrelated reads`() = FileApiFixture().use { api ->
    api.put("value", byteArrayOf(1))
    val downloader = TelegramFileDownloader(api.client())
    downloader.download("value")
    val entered = CountDownLatch(4)
    val release = CountDownLatch(1)
    api.downloadResponse = { path ->
      entered.countDown()
      check(release.await(10, SECONDS))
      api.download(path)
    }
    val pool = Executors.newFixedThreadPool(4)
    try {
      val reads = (1..4).map { pool.submit<ByteArray?> { downloader.download("value") } }
      assertTrue(entered.await(10, SECONDS))
      release.countDown()
      reads.forEach { assertArrayEquals(byteArrayOf(1), it.get(10, SECONDS)) }
      assertEquals(1, api.count("metadata:value"))
      assertEquals(5, api.count("download:value.cbor"))
    } finally {
      release.countDown()
      pool.shutdownNow()
    }
  }

  @Test
  fun `simultaneous misses remain safe without a global network lock`() = FileApiFixture().use { api ->
    api.put("value", byteArrayOf(1))
    val downloader = TelegramFileDownloader(api.client())
    val entered = CountDownLatch(4)
    val release = CountDownLatch(1)
    api.metadataResponse = { id ->
      entered.countDown()
      check(release.await(10, SECONDS))
      api.metadata(id)
    }
    val pool = Executors.newFixedThreadPool(4)
    try {
      val reads = (1..4).map { pool.submit<ByteArray?> { downloader.download("value") } }
      assertTrue(entered.await(10, SECONDS))
      release.countDown()
      reads.forEach { assertArrayEquals(byteArrayOf(1), it.get(10, SECONDS)) }
      assertNotNull(downloader.download("value"))
      assertEquals(4, api.count("metadata:value"))
      assertEquals(5, api.count("download:value.cbor"))
    } finally {
      release.countDown()
      pool.shutdownNow()
    }
  }

  @Test
  fun `old failure cannot evict a newer path supplied by another caller`() = FileApiFixture().use { api ->
    api.put("value", byteArrayOf(1), "old")
    api.contents["new"] = byteArrayOf(2)
    val downloader = TelegramFileDownloader(api.client())
    downloader.download("value")
    val oldEntered = CountDownLatch(1)
    val releaseOld = CountDownLatch(1)
    val oldRequests = AtomicInteger()
    val resolutions = AtomicInteger()
    api.downloadResponse = { path ->
      if (path == "old") {
        if (oldRequests.incrementAndGet() == 1) {
          oldEntered.countDown()
          check(releaseOld.await(10, SECONDS))
        }
        MockResponse().setResponseCode(404)
      } else api.download(path)
    }
    api.metadataResponse = { id ->
      if (resolutions.incrementAndGet() == 1) {
        api.paths[id] = "new"
        api.metadata(id)
      } else MockResponse().setResponseCode(500)
    }
    val pool = Executors.newSingleThreadExecutor()
    try {
      val oldRead = pool.submit<ByteArray?> { downloader.download("value") }
      assertTrue(oldEntered.await(10, SECONDS))
      assertArrayEquals(byteArrayOf(2), downloader.download("value"))
      releaseOld.countDown()
      assertNull(oldRead.get(10, SECONDS))
      assertArrayEquals(byteArrayOf(2), downloader.download("value"))
      assertEquals(3, api.count("metadata:value"))
    } finally {
      releaseOld.countDown()
      pool.shutdownNow()
    }
  }

  @Test
  fun `truncated download returns null like the existing helper after one bounded refresh`() = FileApiFixture().use { api ->
    api.put("value", ByteArray(8192))
    val downloader = TelegramFileDownloader(api.client())
    downloader.download("value")
    api.downloadResponse = { path -> api.download(path).setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY) }
    // Retrofit buffers this endpoint and the bot helper converts its I/O failure to null.
    assertNull(api.client().downloadFileBytes("value"))
    api.requests.clear()
    assertNull(downloader.download("value"))
    assertEquals(listOf("download:value.cbor", "metadata:value", "download:value.cbor"), api.requests.toList())
    api.downloadResponse = null
    assertNotNull(downloader.download("value"))
    assertEquals(2, api.count("metadata:value"))
  }
}
