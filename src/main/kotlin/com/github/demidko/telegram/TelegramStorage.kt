package com.github.demidko.telegram

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.ChatId.Companion.fromChannelUsername
import com.github.kotlintelegrambot.entities.ChatId.Companion.fromId
import com.github.kotlintelegrambot.entities.TelegramFile.ByByteArray
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.serializer
import java.io.Closeable
import java.io.Serializable
import java.lang.Runtime.getRuntime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors.newSingleThreadExecutor

/**
 * Immutable NoSQL database in your Telegram channel.
 * @param K key value type. Should be [basic](https://kotlinlang.org/docs/basic-types.html) or annotated with [Serializable].
 * @param V storable value type. Should be [basic](https://kotlinlang.org/docs/basic-types.html) or annotated with [Serializable].
 * Also see [Telegram Bot API limits](https://core.telegram.org/bots/faq#handling-media)
 */
class TelegramStorage<K, V>(
  private val bot: Bot,
  private val channel: ChatId,
  keySerializer: KSerializer<K>,
  private val valueSerializer: KSerializer<V>,
) : Closeable {

  companion object Constructors {
    /**
     * Immutable NoSQL database in your Telegram channel.
     * @param K key value type. Should be [basic](https://kotlinlang.org/docs/basic-types.html) or annotated with [Serializable].
     * @param V storable value type. Should be [basic](https://kotlinlang.org/docs/basic-types.html) or annotated with [Serializable].
     * Also see [Telegram Bot API limits](https://core.telegram.org/bots/faq#handling-media)
     * @param botToken Telegram bot token. Must be admin of the [channelName]
     * @param channelName Telegram channel name. Do not change the channel description or files!
     */
    inline fun <reified K, reified V> TelegramStorage(
      botToken: String,
      channelName: String
    ): TelegramStorage<K, V> {
      return TelegramStorage(bot { token = botToken }, fromChannelUsername(channelName))
    }

    /**
     * Immutable NoSQL database in your Telegram channel..
     * @param K key value type. Should be [basic](https://kotlinlang.org/docs/basic-types.html) or annotated with [Serializable].
     * @param V storable value type. Should be [basic](https://kotlinlang.org/docs/basic-types.html) or annotated with [Serializable].
     * Also see [Telegram Bot API limits](https://core.telegram.org/bots/faq#handling-media)
     * @param botToken Telegram bot token. Must be admin of the [channelId]
     * @param channelId Telegram channel ID. Do not change the channel description or files!
     */
    inline fun <reified K, reified V> TelegramStorage(
      botToken: String,
      channelId: Long
    ): TelegramStorage<K, V> {
      return TelegramStorage(bot { token = botToken }, fromId(channelId))
    }

    /**
     * Immutable NoSQL database in your Telegram channel.
     * @param K key value type. Should be [basic](https://kotlinlang.org/docs/basic-types.html) or annotated with [Serializable].
     * @param V storable value type. Should be [basic](https://kotlinlang.org/docs/basic-types.html) or annotated with [Serializable].
     * Also see [Telegram Bot API limits](https://core.telegram.org/bots/faq#handling-media)
     * @param bot Telegram bot.
     * See [documentation](https://github.com/kotlin-telegram-bot/kotlin-telegram-bot)
     * @param channel Telegram channel. Use [fromId] or [fromChannelUsername]. The [bot] must be admin of this channel.
     * Do not change the channel description or files!
     */
    inline fun <reified K, reified V> TelegramStorage(bot: Bot, channel: ChatId): TelegramStorage<K, V> {
      return TelegramStorage(bot, channel, serializer<K>(), serializer<V>())
    }
  }

  private val keystoreSerializer = MapSerializer(keySerializer, serializer<String>())

  /**
   * Single thread to safe execution order
   */
  private val atomicExecutor = newSingleThreadExecutor()

  /**
   * Shutdown handler to save [keyToTelegramFileId] to [channel]
   */
  private val shutdownHook = Thread(::close).apply(getRuntime()::addShutdownHook)

  private val keyToTelegramFileId: ConcurrentHashMap<K, String> = run {
    val fileId = bot.getChat(channel).get().description ?: return@run ConcurrentHashMap()
    val bytes = bot.downloadFileBytes(fileId) ?: return@run ConcurrentHashMap()
    val keys = Cbor.decodeFromByteArray(keystoreSerializer, bytes)
    ConcurrentHashMap(keys)
  }

  val size get() = keyToTelegramFileId.size

  val keys get() = keyToTelegramFileId.keys

  fun remove(k: K) {
    val removeFuture = atomicExecutor.submit {
      keyToTelegramFileId.remove(k)
    }
    checkNotNull(removeFuture).get()
  }

  fun clear() {
    val destructionFuture = atomicExecutor.submit {
      keyToTelegramFileId.clear()
      bot.setChatDescription(channel, "")
    }
    checkNotNull(destructionFuture).get()
  }

  fun isEmpty(): Boolean {
    return keyToTelegramFileId.isEmpty()
  }

  operator fun get(k: K): V? {
    val bytes = keyToTelegramFileId[k]?.let(bot::downloadFileBytes) ?: return null
    return Cbor.decodeFromByteArray(valueSerializer, bytes)
  }

  operator fun set(k: K, v: V) {
    val setValueFuture = atomicExecutor.submit {
      val telegramFile = ByByteArray(Cbor.encodeToByteArray(valueSerializer, v))
      keyToTelegramFileId[k] = bot.sendDocument(channel, telegramFile).first?.body()?.result?.document?.fileId!!
    }
    checkNotNull(setValueFuture).get()
  }

  fun containsKey(k: K): Boolean {
    return keyToTelegramFileId.containsKey(k)
  }

  override fun close() {
    val shutdownFuture = atomicExecutor.submit {
      val telegramFile = Cbor.encodeToByteArray(keystoreSerializer, keyToTelegramFileId).let(::ByByteArray)
      val fileId = bot.sendDocument(channel, telegramFile).first?.body()?.result?.document?.fileId!!
      val isSuccessfully = bot.setChatDescription(channel, fileId).first?.isSuccessful!!
      check(isSuccessfully) { "Can't save file id $fileId" }
    }
    checkNotNull(shutdownFuture).get()
    getRuntime().removeShutdownHook(shutdownHook)
  }
}