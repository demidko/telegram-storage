package com.github.demidko.durov

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.TelegramFile
import com.github.kotlintelegrambot.entities.TelegramFile.ByByteArray
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import java.util.concurrent.ConcurrentHashMap

/**
 * Сохранить файл в облако Telegram
 * @return ссылка на файл
 */
internal fun Bot.save(file: TelegramFile, to: ChatId, withTags: Set<String> = emptySet()): Reference {
  val caption = withTags.joinToString(" ") { "#$it" }
  val message = sendDocument(to, file, caption).first?.body()?.result!!
  val fileId = message.document?.fileId!!
  return Reference(message.messageId, fileId)
}

/**
 * @return все ссылки на файлы хранящиеся в этом канале
 */
internal fun Bot.readReferences(channel: ChatId): ReferencesMap {
  val fileId = getChat(channel).get().description ?: return ConcurrentHashMap()
  val protobuf = downloadFileBytes(fileId) ?: return ConcurrentHashMap()
  return try {
    return ConcurrentHashMap(ProtoBuf.decodeFromByteArray<ReferencesMap>(protobuf))
  } catch (e: SerializationException) {
    ConcurrentHashMap()
  }
}

/**
 * Сохранить ссылки из оперативной памяти в канал
 */
internal fun Bot.save(refs: ReferencesMap, to: ChatId) {
  val protobuf = ProtoBuf.encodeToByteArray(refs)
  val fileId = save(ByByteArray(protobuf), to).fileId
  if (setChatDescription(to, fileId).first?.isSuccessful != true) {
    error("Can't save file id $fileId")
  }
}