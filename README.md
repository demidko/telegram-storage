# Telegram Storage

Telegram cloud client. You need a channel (name or ID) in which your bot has full administrator
rights. Do not change the channel description! The bot stores the keystore identifier in the channel
description.

## Download

You need Gradle, or Maven, or other build tool

[![](https://jitpack.io/v/demidko/telegram-storage.svg)](https://jitpack.io/#demidko/telegram-storage)

Also, you need to add [Kotlin serialization plugin](https://github.com/Kotlin/kotlinx.serialization), for example, in
_build.gradle.kts_

```kotlin
plugins {
    kotlin("plugin.serialization") version "2.1.20-Beta2"
}
```

## Usage example

```kotlin
import com.github.demidko.telegram.*
import kotlinx.serialization.Serializable
import com.github.demidko.telegram.TelegramStorage.Companion.newTelegramStorage

@Serializable
data class Person(val name: String, val address: String)

fun main() {
    val token = "Example Telegram Bot API access token"
    val channel = "Example channel name or numeric id"
    val storage = newTelegramStorage(token, channel)

    storage["id"] = Person("Elon Musk", "Texas") // saved to Telegram channel

    val p: Person = storage["id"] // restored Person("Elon Musk", "Texas") from Telegram channel
}
```
