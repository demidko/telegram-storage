package com.github.demidko.durov

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.ChatId.Companion.fromChannelUsername
import com.github.kotlintelegrambot.entities.ChatId.Companion.fromId
import com.github.kotlintelegrambot.entities.TelegramFile
import com.github.kotlintelegrambot.entities.TelegramFile.*
import java.io.Closeable
import java.io.File
import java.lang.Runtime.getRuntime
import java.net.URL

/**
 * Immutable database based on Telegram channels.
 * @param channel Important! Do not change channel description!
 * @see <a href="https://core.telegram.org/bots/faq#handling-media">Telegram Bots API limits</a>
 */
@Suppress("MemberVisibilityCanBePrivate")
class Durov(val bot: Bot, val channel: ChatId) : Closeable {

  private val refs = bot.readReferences(channel)
  private val onShutdown = Thread { bot.save(refs, channel) }

  init {
    getRuntime().addShutdownHook(onShutdown)
  }

  fun save(tgFile: TelegramFile, tags: Set<String>) = refs.add(bot.save(tgFile, channel, tags), tags)
  fun save(url: URL, tags: Set<String>) = save(ByUrl(url.toString()), tags)
  fun save(file: File, tags: Set<String>) = save(ByFile(file), tags)
  fun save(bytes: ByteArray, tags: Set<String>) = save(ByByteArray(bytes), tags)
  fun save(text: String, tags: Set<String>) = save(ByByteArray(text.toByteArray()), tags)
  fun lookupForBytes(tags: Set<String>) = refs.lookup(tags).map(Reference::fileId).mapNotNull(bot::downloadFileBytes)
  fun lookupForMessages(tags: Set<String>) = refs.lookup(tags).map(Reference::messageId)
  fun lookupForText(tags: Set<String>) = lookupForBytes(tags).map(::String)

  override fun close() {
    onShutdown.run()
    onShutdown.join()
    getRuntime().removeShutdownHook(onShutdown)
  }
}

/**
 * Immutable database based on Telegram channels.
 * @param channelName Important! Do not change channel description!
 * @see <a href="https://core.telegram.org/bots/faq#handling-media">Telegram Bots API limits</a>
 */
fun Durov(botToken: String, channelName: String) = Durov(
  bot { token = botToken },
  fromChannelUsername(channelName)
)

/**
 * Immutable database based on Telegram channels.
 * @param channelId Important! Do not change channel description!
 * @see <a href="https://core.telegram.org/bots/faq#handling-media">Telegram Bots API limits</a>
 */
fun Durov(botToken: String, channelId: Long) = Durov(
  bot { token = botToken },
  fromId(channelId)
)