# Telegram Storage

This library is your `Map<K, V>` in the Telegram channel. To try it, your bot needs a channel (name or ID) with
full admin rights.

## Warnings

* You can save only 20 entries per minute with a single token. If the limit is exceeded, waiting will occur.
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
import com.github.demidko.telegram.TelegramStorage.Constructors.TelegramStorage

@Serializable data class Person(val name: String, val address: String)

fun main() {
    val token = "Bot API token here"
    val channel = "Telegram channel name here" // or long id
    val storage = TelegramStorage<String, Person>(token, channel)

    // saved to Telegram channel
    storage["Special Government Employee"] = Person("Elon Musk", "Texas")

    // restored Person("Elon Musk", "Texas") from channel
    val p = storage["Special Government Employee"]!!
}
```

## Repeated reads

By default, every read resolves metadata through `getFile`, preserving the existing
metadata checks and failure behavior. Repeated-read workloads can explicitly opt in:

```kotlin
val storage = TelegramStorage<String, Person>(token, channel, cacheDownloadPaths = true)
```

When enabled, reads reuse resolved Telegram download paths for up to 30 minutes, with at most 1,024
paths per storage instance. The cache is keyed by file ID and never stores file contents
or decoded values: each successful read still downloads and decodes its value.
[Telegram guarantees download links for at least one hour](https://core.telegram.org/bots/api#getfile).

Expiry uses a monotonic clock starting before the metadata request. If a cached download
fails, the storage discards that path and makes one fresh metadata lookup and download
attempt. Changing a key's file ID uses the new ID; removing a key still makes it absent.

With caching enabled, a warm read with a valid download path can succeed during a metadata-only API outage;
it does not contact `getFile` until expiry or a failed download. Cold or expired lookups
still return `null` when metadata resolution fails. This changes metadata-error visibility
on warm reads, but does not serve cached value data. Writes and close behavior are unchanged.

## Local validation

`./gradlew test` runs offline HTTP fixtures when `BOT_TOKEN` and `CHANNEL_NAME` are unset.
The existing destructive integration suite runs only when both variables are nonempty,
so credentialed upstream CI keeps its coverage. Use a disposable channel when enabling it.

`./gradlew benchmarkReads` measures default reads against a local fixture. Add
`-PcacheDownloadPaths=true` to measure the opt-in cache on hot, cold and mixed workloads.
Use `-PpayloadBytes=1048576` for 1 MiB values. See [measurements and limitations](benchmarks/reads.md).
