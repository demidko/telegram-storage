package com.github.demidko.telegram

import com.github.demidko.telegram.TelegramStorage.Constructors.TelegramStorage
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.*
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation
import java.lang.System.getenv
import kotlin.random.Random.Default.nextInt

/**
 * **You need provide BOT_TOKEN and CHANNEL_NAME environment variables for IT test**
 */
@TestMethodOrder(OrderAnnotation::class)
object TelegramStorageIT {

  private val botToken = getenv("BOT_TOKEN")

  private val channelName = getenv("CHANNEL_NAME")

  @Serializable
  data class Person(val name: String, val address: String)

  private lateinit var employees: TelegramStorage<String, Person>

  private val employeesForRateLimiterTest = (1..30).map {
    Pair(
      nextInt().toString(),
      Person(
        "[TOP SECRET] MOVED PERSON ID${nextInt()}",
        "Special St Bldg ${listOf('A', 'B', 'C', 'D', 'G').random()} Rm ${nextInt(15, 2000)}"
      )
    )
  }

  @BeforeEach
  fun openChannelStorage() {
    employees = TelegramStorage(botToken, channelName)
  }

  @AfterEach
  fun closeChannelStorage() {
    employees.close()
  }

  @Test
  @Order(1)
  fun testSave() {
    employees["Special Government Employee"] = Person("Elon Musk", "Texas")
  }

  @Test
  @Order(2)
  fun testDownload() {
    assertThat(employees["Special Government Employee"]).isEqualTo(Person("Elon Musk", "Texas"))
  }

  @Test
  @Order(3)
  fun testSaveWithRateLimiter() {
    for ((i, p) in employeesForRateLimiterTest) {
      employees[i] = p
    }
  }

  @Test
  @Order(4)
  fun testDownloadAfterRateLimiter() {
    val expectedPersons = employeesForRateLimiterTest.map(Pair<String, Person>::second)
    val downloadedPersons = employeesForRateLimiterTest.map { employees[it.first] }
    assertThat(downloadedPersons).isEqualTo(expectedPersons)
  }

  @AfterAll
  @JvmStatic
  fun clearChannelStorage() {
    openChannelStorage()
    employees.clear()
    closeChannelStorage()
  }
}