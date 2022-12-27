package com.github.demidko.telegram

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.ChatId.Companion.fromChannelUsername
import com.github.kotlintelegrambot.entities.ChatId.Companion.fromId
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import java.io.Closeable

/**
 * Immutable nosql database in your Telegram channel.
 *
 * @param T storable value type. Should be [basic](https://kotlinlang.org/docs/basic-types.html) or annotated with [Serializable]
 * @param bot Telegram bot.
 * See [documentation](https://github.com/kotlin-telegram-bot/kotlin-telegram-bot)
 * @param channel Telegram channel. Channel description must be empty!
 * Use [fromId] or [fromChannelUsername]. The [bot] must be admin of this channel.
 * Do not change the channel description and files in this channel!
 */
class TelegramStorage(private val bot: Bot, private val channel: ChatId) : Closeable {

  /**
   * Low-level API.
   */
  val bytes = BytesTelegramStorage(bot, channel)

  val size get() = bytes.size

  fun remove(key: String) = bytes.remove(key)

  /**
   * @param v see [Telegram Bot API limits](https://core.telegram.org/bots/faq#handling-media)
   */
  inline operator fun <reified V> set(k: String, v: V) {
    bytes[k] = Cbor.encodeToByteArray(v)
  }

  inline operator fun <reified V> get(k: String): V? {
    val bytes = bytes[k] ?: return null
    return Cbor.decodeFromByteArray<V>(bytes)
  }

  override fun close() {
    bytes.close()
  }
}