package com.github.demidko.telegram

import com.github.demidko.telegram.TelegramStorage.Companion.newTelegramStorage
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.lang.System.getenv

/**
 * You need provide BOT_TOKEN and CHANNEL_NAME environment variables for IT test
 */
class TelegramStorageIT {

  @Serializable
  data class Person(
    val name: String,
    val address: String,
    val bankIdToMoney: Map<Long, Long>
  )

  private lateinit var storage: TelegramStorage<String, Person>

  @BeforeEach
  fun openChannelStorage() {
    val botToken = getenv("BOT_TOKEN")
    val channelName = getenv("CHANNEL_NAME")
    storage = newTelegramStorage(botToken, channelName)
  }

  @AfterEach
  fun closeChannelStorage() {
    storage.close()
  }

  @Test
  fun testSave() {
    storage["id"] = Person("Elon Musk", "Texas", mapOf(1L to 100L))
  }

  @Test
  fun testDownload() {
    val person: Person = storage["id"]!!
    assertThat(person).isEqualTo(Person("Elon Musk", "Texas", mapOf(1L to 100L)))
  }
}