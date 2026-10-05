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

## Update: download-path caching is enabled by default

Reads now reuse resolved Telegram download paths for up to 30 minutes, with at most
1,024 paths per storage instance. The cache is keyed by file ID and never stores file
contents or decoded values: each successful read still downloads and decodes its value.
[Telegram guarantees download links for at least one hour](https://core.telegram.org/bots/api#getfile).

This changes metadata-error visibility: a warm read can succeed while `getFile` is
unavailable or rejects metadata resolution, provided the previously resolved download
URL still works. Metadata-only removal does not immediately revoke that URL. Cold or
expired reads still resolve metadata and can return `null` when resolution fails.

To restore the original per-read metadata checks and failure behavior, opt out:

```kotlin
val storage = TelegramStorage<String, Person>(token, channel, cacheDownloadPaths = false)
```

The same option is available for channel IDs, bot instances and the serializer-based
constructor. Existing calls enable caching automatically when upgraded to this version;
consumers pinned to an older JitPack version retain that version's behavior.

Expiry uses a monotonic clock starting before the metadata request. A failed cached
download discards that path and permits one fresh metadata lookup and download attempt.
Changing a key's file ID uses the new ID; removing a key still makes it absent.
Writes, close and shutdown-hook behavior are unchanged.

In local fixtures, 100 hot reads used 100 HTTP requests instead of 200 (50% fewer);
an 80%-hot mixed workload used 120 (40% fewer). Cold scans saved no requests. Benefits
depend on revisiting file IDs within the cache's lifetime and capacity. Failed cached
reads can need an extra download attempt. These are local request-count results, not
live Telegram latency guarantees; see [measurements and limitations](benchmarks/reads.md).

## Local validation

`./gradlew test` runs offline HTTP fixtures when `BOT_TOKEN` and `CHANNEL_NAME` are unset.
The existing destructive integration suite runs only when both variables are nonempty,
so credentialed upstream CI keeps its coverage. Use a disposable channel when enabling it.

`./gradlew benchmarkReads` measures default reads against a local fixture. Add
`-PcacheDownloadPaths=false` to measure the original metadata behavior on hot, cold and mixed workloads.
Use `-PpayloadBytes=1048576` for 1 MiB values. See [measurements and limitations](benchmarks/reads.md).
