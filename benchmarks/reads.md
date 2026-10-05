# Download-path cache: behavior and measurements

With `cacheDownloadPaths = false`, `TelegramStorage.get` calls the dependency's `downloadFileBytes` every time.
In kotlin-telegram-bot 6.3.0, that resolves the file with `getFile` and then downloads it:
[versioned implementation](https://github.com/kotlin-telegram-bot/kotlin-telegram-bot/blob/6.3.0/telegram/src/main/kotlin/com/github/kotlintelegrambot/Bot.kt#L1259).
The download URL format matches the [official getFile endpoint](https://core.telegram.org/bots/api#getfile),
which guarantees links for at least one hour and allows resolving a new link when one expires.

The default `cacheDownloadPaths = true` mode reuses only that path. It retains at most 1,024 file-ID/path entries per
storage instance, using the already-required Guava cache. No new production dependency
is introduced. Existing constructor and factory JVM signatures are retained with
`@JvmOverloads`; the optional Boolean defaults to true. Opted-out instances allocate no cache. Heap usage depends on ID/path string
lengths; this is an entry-count bound, not a fixed byte budget. Value payloads and decoded
objects are never retained by the cache. Expired entries are cleaned up during cache
maintenance; there is no timer thread or new shutdown behavior.

## Compatibility assessment

- Paths expire 30 minutes after the metadata request starts, measured by Guava's monotonic
  system ticker. Hits and long downloads do not renew that lifetime. A deliberately slow
  metadata response cannot extend it either. Fake-clock tests avoid timing-dependent sleeps.
- Each read checks the current key-to-file-ID mapping before consulting the cache. Set uses
  its newly uploaded file ID; remove, the mutable keys view and clear retain their behavior.
  Each successful read downloads fresh content and decodes a new value, including mutable values.
- A failed cached download evicts only the entry that failed, then performs one fresh metadata
  lookup and download attempt. At the application level this is at most two downloads and one
  metadata lookup for that read. The dependency's own connection-retry policy is unchanged.
  Cold/expired reads make only one resolution/download attempt. Failed resolutions/downloads
  are not cached. A failed refresh does not return old contents.
- Concurrent warm reads can download independently. Simultaneous cold misses can each resolve
  metadata; they are not coalesced. A delayed failure cannot evict a newer entry from another caller.
  Cached paths are never shared across storage instances or bot contexts.
- Invalid CBOR still raises its decoding error. The pinned Retrofit endpoint buffers response
  bodies; a truncated transfer normally becomes a null result in the existing bot helper. Tests
  compare that behavior directly. An IOException thrown while reading a returned body remains
  an exception and evicts the path.
- **Observable error-path difference:** if a cached URL still serves content while `getFile`
  is unavailable (or rejects metadata resolution), a warm read can succeed. Cold/expired reads
  still attempt metadata and can return null. Likewise, removal of metadata alone is not an
  immediate revocation of an already-valid download URL; removal of both returns null. The
  official reuse guarantee supports the success-path optimization, but it does not make the
  disabled and enabled metadata-error observations identical. Caching is enabled by default;
  set `cacheDownloadPaths = false` to call the original SDK helper on every read and restore
  the original metadata failure behavior.
- `close`, shutdown-hook registration, `set`, `remove` and `clear` are byte-for-byte unchanged
  from upstream. Constructor index loading, persistence ordering, write throttling and
  cross-instance consistency are unchanged. No value caching or write deduplication is added.

## Reproduction

The fixture-only baseline commit `0289e7b` retains upstream production code from
`0bfa8bc544042f3253bc0ed4db7ebe7076653a75`. The results below, measured with production/benchmark commit `2c80267`, compare explicitly disabled and
enabled modes using exactly the same harness. The subsequent default-on change only changes
the omitted option value; the explicitly configured paths measured here are unchanged.
A final no-option `benchmarkReads` run also confirmed `cache_enabled=true` and the same
request totals (hot 100, cold 200, mixed 120 per 100 reads).
Use JDK 23 with the repository's Gradle 8.12.1 wrapper, with live credentials unset:

```sh
unset BOT_TOKEN CHANNEL_NAME
./gradlew build
./gradlew benchmarkReads -PcacheDownloadPaths=false
./gradlew benchmarkReads -PcacheDownloadPaths=true
./gradlew benchmarkReads -PpayloadBytes=1048576 -PcacheDownloadPaths=false
./gradlew benchmarkReads -PpayloadBytes=1048576 -PcacheDownloadPaths=true
```

All HTTP uses a loopback fixture and the dummy token `local`. No artificial response delay
is injected. Each implementation uses the same fixture, payload validation and benchmark.
Each workload warms up with 16 reads of eight hot keys, followed by five batches of 20 reads:

- Hot: all measured reads reuse those eight keys.
- Cold: every measured read uses a distinct, previously unread key.
- Mixed: 80% hot reads and 20% distinct cold reads.

Initialization, warmup and close are excluded from the measured region. Constructor and
close behavior are still executed; every close is checked to make both original write
requests. Each payload/implementation run uses a fresh JVM. The final recorded runs were
sequential: disabled 1 KiB, enabled 1 KiB, disabled 1 MiB, enabled 1 MiB. No benchmark
processes competed with each other. The wall-clock date was 2026-10-05, Linux x86_64,
Eclipse Temurin 23.0.2+7. All 60 batch observations are in [read-results.csv](read-results.csv).

## Request counts

Totals for 100 measured reads, identical for both payload sizes; initialization/warmup/close excluded:

| Workload | Disabled metadata + downloads | Enabled metadata + downloads | HTTP request reduction |
| --- | ---: | ---: | ---: |
| Hot | 100 + 100 | 0 + 100 | 50% |
| Cold | 100 + 100 | 100 + 100 | 0% |
| Mixed | 100 + 100 | 20 + 100 | 40% |

These exact request savings are the primary result. The workload must revisit file IDs
within the lifetime and capacity of a storage instance's cache to benefit.

## Timing observations

Median of five batch means, milliseconds per read:

| Payload | Workload | Disabled | Enabled |
| --- | --- | ---: | ---: |
| 1 KiB | Hot | 89.474 | 44.385 |
| 1 KiB | Cold | 88.995 | 88.747 |
| 1 KiB | Mixed | 88.990 | 53.185 |
| 1 MiB | Hot | 60.598 | 32.232 |
| 1 MiB | Cold | 71.635 | 73.868 |
| 1 MiB | Mixed | 47.980 | 40.386 |

These are local HTTP/JVM observations, not live Telegram latency or throughput. They include
client/server socket behavior, content checks, allocations, JIT and GC effects. In particular,
the 1 MiB cold control changed timing despite identical request counts; not all timing movement
can be attributed to the cache. Do not extrapolate a universal speedup from these numbers.
Write throughput is unchanged, and cold scans do not save requests. Failed cached reads may
need an additional download attempt and can take longer than the old path.

## Validation

Twenty-two local tests pass (15 downloader tests and seven storage integration tests), covering
expiry, slow resolution, bounded entries, fresh mutable values, replacement/removal, metadata
outages, missing paths, deleted files, bounded refresh, truncated transfers, concurrent misses,
concurrent hits and conditional invalidation. Opt-out metadata failures, default-enabled
factory and constructor behavior, the original four-argument JVM constructor, and original factory descriptors
are also covered. An independent read-only review found no issues. Four live integration tests were skipped with
credentials unset. No real Telegram account, credential or live data was used.

The cloud environment used `JAVA_HOME=/workspace/tools/jdk-23.0.2+7`,
`GRADLE_USER_HOME=/workspace/.gradle`, and
`-Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts`. These local settings are not required
repository changes. Real-service timing and actual workload hit rates remain unmeasured.
