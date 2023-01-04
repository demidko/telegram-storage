# Telegram Storage

Telegram cloud client. You need a channel (name or ID) in which your bot has full administrator
rights. Do not change the channel description! The bot stores the keystore identifier in the channel
description.

## Download

_Supports Kotlin 1.8.0-RC2 and higher._ You need Gradle, or Maven, or other build tool. 

[![](https://jitpack.io/v/demidko/telegram-storage.svg)](https://jitpack.io/#demidko/telegram-storage)

Also, you need to add Kotlin serialization plugin, for example, in _build.gradle.kts_
```kotlin
plugins {
    kotlin("plugin.serialization") version "1.8.0-RC2"
}
```

## Usage example

```kotlin
import com.github.demidko.telegram.*
import kotlinx.serialization.Serializable

@Serializable
data class People(val name: String, val address: String)

fun main() {
    val token = "Example Telegram Bot API access token"
    val channel = "Example channel name or numeric id"
    val storage = TelegramStorage(token, channel)

    storage["id"] = People("Elon Musk", "Texas")

    val obj: People = storage["id"] // People("Elon Musk", "Texas")
}
```
