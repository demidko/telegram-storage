# Telegram Storage

Telegram cloud client. You need a channel (name or ID) in which your bot has full administrator
rights.  
**Do not change the channel description!** The bot stores the keystore identifier in the channel
description.

## Download

You need Gradle, or Maven, or other build tool.

[![](https://jitpack.io/v/demidko/telegram-storage.svg)](https://jitpack.io/#demidko/telegram-storage)

Also, you need add Kotlin serialization plugin, for example, in _build.gradle.kts_
```kotlin
plugins {
    kotlin("plugin.serialization") version "1.6.0"
}
```


## Usage example

```kotlin
import kotlinx.serialization.Serializable

import java.lang.System.getenv
import com.github.demidko.telegram.*

@Serializable
data class People(val name: String, val address: String)

fun main() {
    val botToken = "Example Telegram Bot API access token"
    val channelName = "@example_channel_name" // You can also use channel id
    val storage = TelegramStorage<People>(botToken, channelName)
    storage["id"] = People("Elon Musk", "Texas")
    val restoredObject: People = storage["id"] // People("Elon Musk", "Texas")
}
```
