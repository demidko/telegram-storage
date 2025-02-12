package com.github.demidko.telegram

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.ChatId.Companion.fromChannelUsername
import com.github.kotlintelegrambot.entities.ChatId.Companion.fromId
import com.github.kotlintelegrambot.entities.TelegramFile.ByByteArray
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import java.io.Closeable
import java.lang.Runtime.getRuntime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors.newSingleThreadExecutor
import java.util.concurrent.Future

class TelegramStorage<K, V>(
  private val bot: Bot,
  private val channel: ChatId,
  private val keyToTelegramFileId: MutableMap<K, String>
) : Closeable {

  companion object {
    inline fun <reified K, V> newTelegramStorage(botToken: String, channelName: String): TelegramStorage<K, V> {
      return newTelegramStorage(bot { token = botToken }, fromChannelUsername(channelName))
    }

    inline fun <reified K, V> newTelegramStorage(botToken: String, channelId: Long): TelegramStorage<K, V> {
      return newTelegramStorage(bot { token = botToken }, fromId(channelId))
    }

    /**
     * Immutable nosql database in your Telegram channel.
     *
     * @param K key value type. Should be [basic](https://kotlinlang.org/docs/basic-types.html) or annotated with [Serializable].
     *
     * @param V storable value type. Should be [basic](https://kotlinlang.org/docs/basic-types.html) or annotated with [Serializable].
     * Also see [Telegram Bot API limits](https://core.telegram.org/bots/faq#handling-media)
     *
     * @param bot Telegram bot.
     * See [documentation](https://github.com/kotlin-telegram-bot/kotlin-telegram-bot)
     *
     * @param channel Telegram channel.
     * Use [fromId] or [fromChannelUsername]. The [bot] must be admin of this channel.
     * Do not change the channel description or files!
     *
     */
    inline fun <reified K, V> newTelegramStorage(bot: Bot, channel: ChatId): TelegramStorage<K, V> {
      val map = readKeysWithTelegramFileIds<K>(bot, channel)
      return TelegramStorage(bot, channel, ConcurrentHashMap(map))
    }

    inline fun <reified K> readKeysWithTelegramFileIds(bot: Bot, channel: ChatId): Map<K, String> {
      val fileId = bot.getChat(channel).get().description ?: return emptyMap()
      val bytes = bot.downloadFileBytes(fileId) ?: return emptyMap()
      return Cbor.decodeFromByteArray<Map<K, String>>(bytes)
    }
  }

  /**
   * Single thread to safe execution order
   */
  private val atomicExecutor = newSingleThreadExecutor()

  /**
   * Shutdown handler to save [keyToTelegramFileId] to [channel]
   */
  private val onShutdown = Thread {
    atomicExecutor.submit {
      val map = keyToTelegramFileId.toMap()
      val telegramFile = Cbor.encodeToByteArray(map).let(::ByByteArray)
      val fileId = bot.sendDocument(channel, telegramFile).first?.body()?.result?.document?.fileId!!
      val isSuccessfully = bot.setChatDescription(channel, fileId).first?.isSuccessful!!
      check(isSuccessfully) { "Can't save file id $fileId" }
    }.get()
  }.apply(getRuntime()::addShutdownHook)

  val size get() = keyToTelegramFileId.size

  fun remove(k: K): Future<*>? {
    return atomicExecutor.submit {
      keyToTelegramFileId.remove(k)
    }
  }

  val keys get() = keyToTelegramFileId.keys

  fun setBinaryFuture(k: K, v: ByteArray): Future<*>? {
    return atomicExecutor.submit {
      val telegramFile = ByByteArray(v)
      keyToTelegramFileId[k] = bot.sendDocument(channel, telegramFile).first?.body()?.result?.document?.fileId!!
    }
  }

  fun setBinary(k: K, v: ByteArray) {
    checkNotNull(setBinaryFuture(k, v)).get()
  }

  inline operator fun <reified V1 : V> get(k: K): V1? {
    val bytes = getBinary(k) ?: return null
    return Cbor.decodeFromByteArray<V1>(bytes)
  }

  inline fun <reified V1 : V> setFuture(k: K, v: V1): Future<*>? {
    return setBinaryFuture(k, Cbor.encodeToByteArray(v))
  }

  inline operator fun <reified V1 : V> set(k: K, v: V1) {
    checkNotNull(setFuture(k, v)).get()
  }

  fun getBinary(k: K): ByteArray? {
    return keyToTelegramFileId[k]?.let(bot::downloadFileBytes)
  }

  override fun close() {
    onShutdown.start()
    onShutdown.join()
    getRuntime().removeShutdownHook(onShutdown)
  }
}