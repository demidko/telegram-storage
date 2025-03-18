package com.github.demidko.telegram

import com.github.demidko.telegram.TelegramStorage.Constructors.TelegramStorage
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.lang.System.getenv

/**
 * **You need provide BOT_TOKEN and CHANNEL_NAME environment variables for IT test**
 */
object TelegramStorageIT {

  private val botToken = getenv("BOT_TOKEN")

  private val channelName = getenv("CHANNEL_NAME")

  @Serializable
  data class Person(val name: String, val address: String)

  private lateinit var employees: TelegramStorage<String, Person>

  @BeforeEach
  fun openChannelStorage() {
    employees = TelegramStorage(botToken, channelName)
  }

  @AfterEach
  fun closeChannelStorage() {
    employees.close()
  }

  @Test
  fun testSave() {
    employees["Special Government Employee"] = Person("Elon Musk", "Texas")
  }

  @Test
  fun testDownload() {
    assertThat(employees["Special Government Employee"]).isEqualTo(Person("Elon Musk", "Texas"))
  }

  @AfterAll
  @JvmStatic
  fun clearChannelStorage() {
    openChannelStorage()
    employees.clear()
  }
}