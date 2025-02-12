# Telegram Storage

This library is your quick `Map<K, V>` in the Telegram channel. To try it, your bot needs a channel (name or ID) with
full admin rights.

## Warnings

* Don't change the description—the bot stores the keystore file ID there
* After the first setup, you can't change the dictionary's key/value types

## Download

You need Gradle, Maven, or another build tool

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
import com.github.demidko.telegram.TelegramStorage.Constructors.TelegramStorage

@Serializable
data class Person(val name: String, val address: String)

fun main() {
    val token = "Bot API token here"
    val channel = "Telegram channel name here"
    val storage = TelegramStorage<Int, Person>(token, channel)
    
    storage[2] = Person("Elon Musk", "Texas") // saved to Telegram channel

    val p = storage[2] // restored Person("Elon Musk", "Texas") from channel
}
```
