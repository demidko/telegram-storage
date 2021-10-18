package com.github.demidko.channelstorage

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.TelegramFile.ByByteArray
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import java.util.concurrent.ConcurrentHashMap

/**
 * Сохранить ссылки на файлы канала в канал как еще один файл.
 * Ссылка на этот ключевой файл в свою очередь будет сохранена в описание канала.
 */
internal fun Bot.saveFilesReferences(filesReferences: MutableMap<Any, String>, to: ChatId) {
  val protobuf = ByByteArray(ProtoBuf.encodeToByteArray(filesReferences))
  val fileId = sendDocument(to, protobuf).first?.body()?.result?.document?.fileId!!
  val isSuccessfully = setChatDescription(to, fileId).first?.isSuccessful!!
  check(isSuccessfully) { "Can't save file id $fileId" }
}

/**
 * Прочитать файл-карту из канала. Ссылка на него ожидается в описании канала.
 * @return словарь вида {ключ: идентификатор файла}.
 */
internal fun Bot.readFilesReferences(channel: ChatId): MutableMap<Any, String> {
  val fileId = getChat(channel).get().description ?: return ConcurrentHashMap()
  val protobuf = downloadFileBytes(fileId) ?: return ConcurrentHashMap()
  return try {
    val decodedMap = ProtoBuf.decodeFromByteArray<Map<Any, String>>(protobuf)
    return ConcurrentHashMap(decodedMap)
  } catch (e: SerializationException) {
    ConcurrentHashMap()
  }
}

