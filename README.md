# ChannelStorage

Telegram cloud client. You need a channel (name or ID) in which your bot has full administrator
rights.  
**Do not change the channel description!** The bot stores the keystore identifier in the channel
description.

## Download

You need Gradle, or Maven, or other build tool.

[![](https://jitpack.io/v/demidko/channelstorage.svg)](https://jitpack.io/#demidko/channelstorage)

## Usage

```kotlin
import com.github.demidko.channelstorage.ChannelStorage

fun main() {
  val storage = ChannelStorage("your bot token", "your channel name")
  storage["your key"] = 5 // save value
  storage["yor key 2"] = "test" // save value
  val firstValue: Int? = storage["your key"] // read value
  val secondValue: String? = storage["your key 2"] // read value
}
```
