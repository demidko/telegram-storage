package com.github.demidko.channelstorage

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.ChatId.Companion.fromChannelUsername
import com.github.kotlintelegrambot.entities.ChatId.Companion.fromId
import com.github.kotlintelegrambot.entities.TelegramFile
import com.github.kotlintelegrambot.entities.TelegramFile.ByByteArray
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import java.io.Closeable
import java.lang.Runtime.getRuntime
import java.util.concurrent.Executors.newSingleThreadExecutor

/**
 * Immutable nosql database in your Telegram channel.
 */
class ChannelStorage(private val bot: Bot, private val channel: ChatId) : Closeable {

  /**
   * Map: your key -> bot file id
   */
  private val fileIdentifiers = bot.readFilesReferences(channel)

  /**
   * Single thread to safe execution order
   */
  private val atomicExecutor = newSingleThreadExecutor()

  /**
   * Shutdown handler to save [fileIdentifiers] to Telegram
   */
  private val onShutdown =
    Thread { bot.saveFilesReferences(fileIdentifiers, channel) }
      .apply(getRuntime()::addShutdownHook)

  constructor(botToken: String, channelName: String)
    : this(bot { token = botToken }, fromChannelUsername(channelName))

  constructor(botToken: String, channelId: Long)
    : this(bot { token = botToken }, fromId(channelId))


  /**
   * Save [fileIdentifiers] to Telegram and cleanup shutdown hook
   */
  override fun close() {
    atomicExecutor.submit {
      onShutdown.run()
      onShutdown.join()
      getRuntime().removeShutdownHook(onShutdown)
    }
  }

  val size get() = fileIdentifiers.size

  fun <K> remove(k: K) = fileIdentifiers.remove(k as Any)

  /**
   * @param v see [Telegram Bot API limits](https://core.telegram.org/bots/faq#handling-media)
   */
  inline operator fun <K, reified V> set(k: K, v: V) {
    this[k] = ByByteArray(ProtoBuf.encodeToByteArray(v))
  }

  /**
   * Save your value
   * @param k key to value
   * @param v see [Telegram Bot API limits](https://core.telegram.org/bots/faq#handling-media)
   */
  operator fun <K> set(k: K, v: TelegramFile) = atomicExecutor.submit {
    fileIdentifiers[k as Any] = bot.sendDocument(channel, v).first?.body()?.result?.document?.fileId!!
  }

  inline operator fun <K, reified V> get(k: K): V? {
    val protobuf = download(k) ?: return null
    return ProtoBuf.decodeFromByteArray<V>(protobuf)
  }

  /**
   * Download yur value
   * @param k your key
   * @return value bytes can be decoded via [decodeFromByteArray] or null
   */
  fun <K> download(k: K): ByteArray? {
    val fileId = fileIdentifiers[k as Any] ?: return null
    return bot.downloadFileBytes(fileId)!!
  }
}
