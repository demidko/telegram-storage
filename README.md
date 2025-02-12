# Telegram Storage

This library is your quick `Map<K, V>` in the Telegram channel. Your bot needs a channel (name or ID) with full admin
rights. Don't change the description—the bot stores key data there.

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
import com.github.demidko.telegram.TelegramStorage.Constructors.newTelegramStorage

@Serializable
data class Person(val name: String, val address: String)

fun main() {
    val token = "Example Telegram Bot API access token"
    val channel = "Example channel name" // or can be numeric id here
    val storage = newTelegramStorage<Int, Person>(token, channel)

    storage[2] = Person("Elon Musk", "Texas") // saved to Telegram channel

    val p: Person = storage[2] // restored Person("Elon Musk", "Texas") from channel
}
```
