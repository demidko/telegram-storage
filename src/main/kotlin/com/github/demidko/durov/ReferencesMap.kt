package com.github.demidko.durov

import kotlinx.serialization.Serializable

/**
 * Ссылка на ресурс сохраненный в канале Telegram
 * @param messageId идентификатор сообщения (уникален для всего Telegram)
 * @param fileId идентификатор файлы (стабилен, уникален для бота Telegram)
 */
@Serializable
internal data class Reference(val messageId: Long, val fileId: String)

/**
 * Структура ставящая в соответствие тегу набор уникальных ссылок на ресурсы.
 * Ссылки могут повторяться для разных тегов.
 */
internal typealias ReferencesMap = MutableMap<String, MutableSet<Reference>>

/**
 * Поиск ссылок помеченных всеми переданными тегами одновременно
 */
internal fun ReferencesMap.lookup(tags: Set<String>) = when (tags.isEmpty()) {
  true -> emptySet()
  else -> tags.map { getOrElse(it, ::setOf) }.reduce { result, refs -> result.intersect(refs) }
}

/**
 * Сохранить ссылку в оперативную память
 */
internal fun ReferencesMap.add(ref: Reference, withTags: Set<String>) = withTags.forEach { tag ->
  getOrPut(tag, ::mutableSetOf).add(ref)
}