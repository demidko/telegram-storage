package com.github.demidko.telegram

import com.github.kotlintelegrambot.Bot
import com.google.common.base.Ticker
import com.google.common.cache.CacheBuilder
import java.io.IOException
import java.util.concurrent.TimeUnit.MINUTES

/** Reuses getFile paths, never file contents. Each instance belongs to one storage/bot context. */
internal class TelegramFileDownloader(
  private val bot: Bot,
  private val ticker: Ticker = Ticker.systemTicker(),
  maximumSize: Long = 1024,
) {
  private class Path(val value: String, val requestedAt: Long)

  // Telegram guarantees getFile links for at least one hour. Keep a conservative margin.
  private val lifetime = MINUTES.toNanos(30)
  private val paths = CacheBuilder.newBuilder()
    .maximumSize(maximumSize)
    .expireAfterWrite(30, MINUTES)
    .ticker(ticker)
    .build<String, Path>()

  fun download(fileId: String): ByteArray? {
    paths.getIfPresent(fileId)?.let { cached ->
      if (fresh(cached)) download(fileId, cached)?.let { return it }
      // Another caller may already have replaced this entry after a failed download.
      paths.asMap().remove(fileId, cached)
    }

    // At most one fresh resolution/download, including after a cached-path failure.
    val requestedAt = ticker.read()
    val response = bot.getFile(fileId).first ?: return null
    if (!response.isSuccessful) {
      response.errorBody()?.close()
      return null
    }
    val value = response.body()?.result?.filePath ?: return null
    val path = Path(value, requestedAt)
    // Start the lifetime before getFile, not after a potentially slow request or download.
    if (fresh(path)) paths.put(fileId, path)
    return download(fileId, path)
  }

  private fun fresh(path: Path) = ticker.read() - path.requestedAt < lifetime

  private fun download(fileId: String, path: Path): ByteArray? {
    val bytes = try {
      val response = bot.downloadFile(path.value).first
      if (response?.isSuccessful == true) response.body()?.bytes() else {
        response?.errorBody()?.close()
        null
      }
    } catch (exception: IOException) {
      paths.asMap().remove(fileId, path)
      // Preserve the existing exception behavior when reading a response body fails.
      throw exception
    }
    if (bytes == null) paths.asMap().remove(fileId, path)
    return bytes
  }
}
