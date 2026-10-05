package com.github.demidko.telegram

import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.cbor.Cbor
import kotlin.system.measureNanoTime

/** Compare this same harness on the baseline and changed production code; no simulated network latency. */
object ReadBenchmark {
  @JvmStatic
  fun main(args: Array<String>) {
    val payloadBytes = args.getOrNull(0)?.toInt() ?: 1024
    val cacheDownloadPaths = args.getOrNull(1)?.toBooleanStrict() ?: true
    val expected = "x".repeat(payloadBytes)
    val encoded = Cbor.encodeToByteArray(String.serializer(), expected)
    for (workload in listOf("hot", "cold", "mixed")) {
      val ids = (0 until 8).map { "hot-$it" } + (0 until 100).map { "cold-$it" }
      FileApiFixture(ids.associateWith { it }).use { api ->
        ids.forEach { api.put(it, encoded) }
        api.open(String.serializer(), cacheDownloadPaths).use { storage ->
          // Identical warmup on both implementations. Cold IDs are not accessed here.
          repeat(16) { check(storage["hot-${it % 8}"] == expected) }
          val means = mutableListOf<Double>()
          repeat(5) { sample ->
            api.requests.clear()
            val elapsed = measureNanoTime {
              repeat(20) { iteration ->
                val n = sample * 20 + iteration
                val key = when (workload) {
                  "cold" -> "cold-$n"
                  "mixed" -> if (n % 5 == 0) "cold-$n" else "hot-${n % 8}"
                  else -> "hot-${n % 8}"
                }
                check(storage[key] == expected)
              }
            } / 1e6 / 20
            means += elapsed
            println("cache_enabled=$cacheDownloadPaths workload=$workload payload_bytes=$payloadBytes sample=${sample + 1} reads=20 mean_ms=$elapsed metadata=${api.count("metadata:")} downloads=${api.count("download:")}")
          }
          println("cache_enabled=$cacheDownloadPaths workload=$workload median_sample_mean_ms=${means.sorted()[2]}")
        }
        check(api.uploads.get() == 1 && api.descriptionUpdates.get() == 1)
      }
    }
  }
}
