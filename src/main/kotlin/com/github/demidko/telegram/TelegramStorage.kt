package com.github.demidko.telegram

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.ChatId.Companion.fromChannelUsername
import com.github.kotlintelegrambot.entities.ChatId.Companion.fromId
import com.github.kotlintelegrambot.entities.TelegramFile.ByByteArray
import com.google.common.util.concurrent.RateLimiter
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.serializer
import java.io.Closeable
import java.io.Serializable
import java.lang.Runtime.getRuntime
import java.lang.Thread.currentThread
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors.newSingleThreadExecutor
import java.util.concurrent.TimeUnit.DAYS
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A free, 1M records NoSQL cloud database in your Telegram channel.
 * See [Telegram Bot API limits](https://core.telegram.org/bots/faq#handling-media)
 * @param K key value type. Should be [basic](https://kotlinlang.org/docs/basic-types.html) or annotated with [Serializable].
 * @param V storable value type. Should be [basic](https://kotlinlang.org/docs/basic-types.html) or annotated with [Serializable].
 * @param bot Telegram bot. Must be admin of the [channel].
 * See [documentation](https://github.com/kotlin-telegram-bot/kotlin-telegram-bot)
 * @param channel Telegram channel. Use [fromId] or [fromChannelUsername]. Do not change the channel description or files!
 */
@Suppress("UnstableApiUsage")
class TelegramStorage<K, V>(
  private val bot: Bot,
  private val channel: ChatId,
  keySerializer: KSerializer<K>,
  private val valueSerializer: KSerializer<V>,
) : Closeable {

  companion object Constructors {
    /**
     * A free, 1M records NoSQL cloud database in your Telegram channel.
     * @param K key value type. Should be [basic](https://kotlinlang.org/docs/basic-types.html) or annotated with [Serializable].
     * @param V storable value type. Should be [basic](https://kotlinlang.org/docs/basic-types.html) or annotated with [Serializable].
     * Also see [Telegram Bot API limits](https://core.telegram.org/bots/faq#handling-media)
     * @param botToken Telegram bot token. Must be admin of the [channelName]
     * @param channelName Telegram channel name. Do not change the channel description or files!
     */
    inline fun <reified K, reified V> TelegramStorage(botToken: String, channelName: String) =
      TelegramStorage<K, V>(bot { token = botToken }, fromChannelUsername(channelName))

    /**
     * A free, 1M records NoSQL cloud database in your Telegram channel.
     * @param K key value type. Should be [basic](https://kotlinlang.org/docs/basic-types.html) or annotated with [Serializable].
     * @param V storable value type. Should be [basic](https://kotlinlang.org/docs/basic-types.html) or annotated with [Serializable].
     * Also see [Telegram Bot API limits](https://core.telegram.org/bots/faq#handling-media)
     * @param botToken Telegram bot token. Must be admin of the [channelId]
     * @param channelId Telegram channel ID. Do not change the channel description or files!
     */
    inline fun <reified K, reified V> TelegramStorage(botToken: String, channelId: Long) =
      TelegramStorage<K, V>(bot { token = botToken }, fromId(channelId))

    /**
     * A free, 1M records NoSQL cloud database in your Telegram channel.
     * @param K key value type. Should be [basic](https://kotlinlang.org/docs/basic-types.html) or annotated with [Serializable].
     * @param V storable value type. Should be [basic](https://kotlinlang.org/docs/basic-types.html) or annotated with [Serializable].
     * Also see [Telegram Bot API limits](https://core.telegram.org/bots/faq#handling-media)
     * @param bot Telegram bot. Must be admin of the [channel].
     * See [documentation](https://github.com/kotlin-telegram-bot/kotlin-telegram-bot)
     * @param channel Telegram channel. Use [fromId] or [fromChannelUsername].
     * Do not change the channel description or files!
     */
    inline fun <reified K, reified V> TelegramStorage(bot: Bot, channel: ChatId) =
      TelegramStorage<K, V>(bot, channel, serializer<K>(), serializer<V>())
  }

  init {
    Thread(::close).apply(getRuntime()::addShutdownHook)
  }

  private val keystoreSerializer = MapSerializer(keySerializer, serializer<String>())

  private val messagesLimiter = RateLimiter.create(0.33)

  /**
   * Single thread to safe execution order
   */
  private val atomicExecutor = newSingleThreadExecutor()

  private val closed = AtomicBoolean(false)

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
    }.get()
  }

  fun clear() {
    val destructionFuture = atomicExecutor.submit {
      keyToTelegramFileId.clear()
      check(bot.setChatDescription(channel, "").unwrap()) { "Failed to clear channel description" }
    }.get()
  }

  fun isEmpty(): Boolean {
    return keyToTelegramFileId.isEmpty()
  }

  operator fun get(k: K): V? {
    val bytes = keyToTelegramFileId[k]?.let(bot::downloadFileBytes) ?: return null
    return Cbor.decodeFromByteArray(valueSerializer, bytes)
  }

  operator fun set(k: K, v: V) {
    atomicExecutor.submit {
      val file = ByByteArray(Cbor.encodeToByteArray(valueSerializer, v))
      messagesLimiter.acquire()
      val fileId = bot.sendDocument(channel, file).unwrap().document?.fileId
      keyToTelegramFileId[k] = checkNotNull(fileId) {
        "Failed to save key $k with value $v. Make sure your bot has the right permissions!"
      }
    }.get()
  }

  fun containsKey(k: K): Boolean {
    return keyToTelegramFileId.containsKey(k)
  }

  override fun close() {
    if (closed.compareAndSet(false, true)) {
      atomicExecutor.submit {
        val telegramFile = Cbor.encodeToByteArray(keystoreSerializer, keyToTelegramFileId).let(::ByByteArray)
        messagesLimiter.acquire()
        val doc = bot.sendDocument(channel, telegramFile).unwrap().document
        checkNotNull(doc) { "Failed to save keystore. Make sure your bot has the right permissions!" }
        messagesLimiter.acquire()
        val isReferenceUpdated = bot.setChatDescription(channel, doc.fileId).unwrap()
        check(isReferenceUpdated) {
          "Failed to update the channel description to save keystore reference: ${doc.fileId}. " +
            "Make sure your bot has the right permissions!"
        }
      }.get()
      var terminated = atomicExecutor.isTerminated
      if (!terminated) {
        atomicExecutor.shutdown()
        var interrupted = false
        while (!terminated) {
          try {
            terminated = atomicExecutor.awaitTermination(1L, DAYS)
          } catch (e: InterruptedException) {
            if (!interrupted) {
              atomicExecutor.shutdownNow()
              interrupted = true
            }
          }
        }
        if (interrupted) {
          currentThread().interrupt()
        }
      }
    }
  }
}

private typealias RetrofitHttpResponse<T> = retrofit2.Response<T>

private typealias TelegramApiResponse<T> = com.github.kotlintelegrambot.network.Response<T>

private fun <T> Pair<RetrofitHttpResponse<TelegramApiResponse<T>?>?, Exception?>.unwrap(): T {
  val (response, exception) = this
  exception?.let { throw it }
  checkNotNull(response) { "Telegram didn't responded" }
  response.errorBody()?.apply { error("Telegram responded HTTP ${response.code()}: ${string() ?: response.message()}") }
  val apiResponse = response.body()
  checkNotNull(apiResponse) { "Telegram responded HTTP ${response.code()}: ${response.message()}" }
  apiResponse.errorCode?.let { error("Telegram responded API error $it: ${apiResponse.errorDescription}") }
  return checkNotNull(apiResponse.result) { "Telegram responded HTTP ${response.code()}: ${response.message()}" }
}