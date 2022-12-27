@file:Suppress("FunctionName")

package com.github.demidko.telegram

import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.entities.ChatId
import kotlinx.serialization.Serializable

/**
 * Immutable nosql database in your Telegram channel.
 *
 * @param T storable value type. Should be [basic](https://kotlinlang.org/docs/basic-types.html) or annotated with [Serializable]
 * @param botToken Telegram bot token.
 * @param channelName Telegram channel name, for example: "test_channel". Channel description must be empty!
 * The bot([botToken]) must be admin of this channel.
 * Do not change the channel description and files in this channel!
 */
fun TelegramStorage(botToken: String, channelName: String): TelegramStorage {
  val bot = bot { token = botToken }
  val channel = ChatId.fromChannelUsername(channelName)
  return TelegramStorage(bot, channel)
}

/**
 * Immutable nosql database in your Telegram channel.
 *
 * @param T storable value type. Should be [basic](https://kotlinlang.org/docs/basic-types.html) or annotated with [Serializable]
 * @param botToken Telegram bot token.
 * @param channelId Telegram channel id, for example: 3242343. Channel description must be empty!
 * The bot([botToken]) must be admin of this channel.
 * Do not change the channel description and files in this channel!
 */
fun TelegramStorage(botToken: String, channelId: Long): TelegramStorage {
  val bot = bot { token = botToken }
  val channel = ChatId.fromId(channelId)
  return TelegramStorage(bot, channel)
}