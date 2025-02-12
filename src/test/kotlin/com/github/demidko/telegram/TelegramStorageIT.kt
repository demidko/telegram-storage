package com.github.demidko.telegram

import com.github.demidko.telegram.TelegramStorage.Constructors.newTelegramStorage
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
  )

  private lateinit var storage: TelegramStorage<Int, Person>

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
    storage[2] = Person("Elon Musk", "Texas")
  }

  @Test
  fun testDownload() {
    val person: Person = storage[2]!!
    assertThat(person).isEqualTo(Person("Elon Musk", "Texas"))
  }
}