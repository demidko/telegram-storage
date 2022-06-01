package com.github.demidko.telegram.experimental

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.ChatId.Companion.fromChannelUsername
import com.github.kotlintelegrambot.entities.ChatId.Companion.fromId
import com.github.kotlintelegrambot.entities.TelegramFile
import com.github.kotlintelegrambot.entities.TelegramFile.ByByteArray
import com.github.kotlintelegrambot.network.fold
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import java.io.Closeable
import java.lang.Runtime.getRuntime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors.newSingleThreadExecutor
import kotlin.collections.MutableMap.MutableEntry

class TelegramMap<K, V>(private val bot: Bot, private val channel: ChatId) : MutableMap<K, V>, Closeable {

  private val identifiers = loadIdentifiers()

  private val executor = newSingleThreadExecutor()

  private val shutdownHook = Thread(::onShutdown).apply(getRuntime()::addShutdownHook)

  constructor(token: String, channelName: String) : this(botOf(token), fromChannelUsername(channelName))

  constructor(token: String, channelId: Long) : this(botOf(token), fromId(channelId))

  override val size get() = identifiers.size

  override val entries: MutableSet<MutableEntry<K, V>> get() = identifiers.keys.map(::TelegramEntry).toMutableSet()

  override val keys get() = identifiers.keys

  override val values: MutableList<V> get() = identifiers.keys.mapNotNull(::get).toMutableList()

  override fun clear() {
    executor.submit { identifiers.clear() }
  }

  override fun isEmpty() = identifiers.isEmpty()

  override fun remove(key: K): V? {
    val previous = get(key)
    executor.submit { identifiers.remove(key) }
    return previous
  }

  override fun putAll(from: Map<out K, V>) {
    for ((k, v) in from) {
      putQuickly(k, v)
    }
  }

  override fun put(key: K, value: V) = put(key, value, true)

  @Suppress("MemberVisibilityCanBePrivate")
  fun putQuickly(key: K, value: V) {
    put(key, value, false)
  }

  private fun put(key: K, value: V, needPrevious: Boolean): V? {
    val previous = if (needPrevious) get(key) else null
    val bytes = Cbor.encodeToByteArray(Holder(value))
    val file = ByByteArray(bytes)
    executor.submit {
      identifiers[key] = bot.sendDocument(channel, file).first?.body()?.result?.document?.fileId!!
    }
    return previous
  }

  override fun get(key: K): V? {
    val id = identifiers[key] ?: return null
    val bytes = bot.downloadFileBytes(id) ?: return null
    return Cbor.decodeFromByteArray<Holder<V>>(bytes).value
  }

  override fun containsValue(value: V) = values.contains(value)

  override fun containsKey(key: K) = identifiers.containsKey(key)

  private fun loadIdentifiers(): MutableMap<K, String> {
    val fileId = bot.getChat(channel).get().description ?: return ConcurrentHashMap()
    val bytes = bot.downloadFileBytes(fileId) ?: return ConcurrentHashMap()
    val map = Cbor.decodeFromByteArray<Map<K, String>>(bytes)
    return ConcurrentHashMap(map)
  }

  private fun saveIdentifiers() {
    val telegramFile = Cbor.encodeToByteArray(identifiers).let(TelegramFile::ByByteArray)
    val fileId = bot.sendDocument(channel, telegramFile).first?.body()?.result?.document?.fileId!!
    bot.setChatDescription(channel, fileId).fold { error("Can't save file id $fileId") }
  }

  private fun onShutdown() = executor.submit(::saveIdentifiers).get()

  override fun close() {
    onShutdown()
    getRuntime().removeShutdownHook(shutdownHook)
  }

  @Serializable
  private class Holder<T>(val value: T)

  inner class TelegramEntry(override val key: K) : MutableEntry<K, V> {

    override val value get() = get(key)!!

    override fun setValue(newValue: V) = put(key, newValue)!!
  }

  private companion object {
    fun botOf(token: String) = bot {
      this.token = token
    }
  }
}