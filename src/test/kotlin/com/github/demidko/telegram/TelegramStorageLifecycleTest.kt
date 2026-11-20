package com.github.demidko.telegram

import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.entities.ChatId
import kotlinx.serialization.builtins.serializer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.SECONDS

class TelegramStorageLifecycleTest {
  @Test
  fun `successful close unregisters its shutdown hook after publishing`() = FileApiFixture(mapOf("key" to "value-file")).use { api ->
    val storage = api.open(String.serializer())
    val hookField = storage.javaClass.getDeclaredField("shutdownHook").apply { isAccessible = true }
    val hook = hookField.get(storage) as Thread
    assertTrue(Runtime.getRuntime().removeShutdownHook(hook), "open storage must remain registered")
    Runtime.getRuntime().addShutdownHook(hook)
    storage.close()
    assertEquals(1, api.uploads.get())
    assertEquals(1, api.descriptionUpdates.get())
    assertFalse(Runtime.getRuntime().removeShutdownHook(hook), "close should have removed its registered hook")
    storage.close()
    assertEquals(1, api.uploads.get())
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(booleans = [false, true])
  fun `duplicate close racing the hook callback does not duplicate publication`(hookWins: Boolean) = FileApiFixture(mapOf("key" to "value-file")).use { api ->
    val storage = api.open(String.serializer())
    val hookField = storage.javaClass.getDeclaredField("shutdownHook").apply { isAccessible = true }
    val hook = hookField.get(storage) as Thread
    val gate = CountDownLatch(1)
    api.descriptionGate = gate
    val callers = Executors.newFixedThreadPool(2)
    try {
      val hookClose = callers.submit { if (hookWins) hook.run() else storage.close() }
      assertTrue(api.descriptionEntered.await(10, SECONDS))
      // Existing semantics: a duplicate close returns even while the winning call is publishing.
      callers.submit { if (hookWins) storage.close() else hook.run() }.get(5, SECONDS)
      assertFalse(hookClose.isDone)
      assertTrue(Runtime.getRuntime().removeShutdownHook(hook), "keep the hook until publication completes")
      Runtime.getRuntime().addShutdownHook(hook)
      gate.countDown()
      hookClose.get(10, SECONDS)
      assertEquals(1, api.uploads.get())
      assertEquals(1, api.descriptionUpdates.get())
      assertFalse(Runtime.getRuntime().removeShutdownHook(hook))
    } finally {
      gate.countDown()
      callers.shutdownNow()
    }
  }

  @Test
  fun `failed close keeps its hook and repeated close does not retry`() = FileApiFixture().use { api ->
    val storage = api.open(String.serializer())
    val hook = storage.javaClass.getDeclaredField("shutdownHook").apply { isAccessible = true }.get(storage) as Thread
    api.failUpload = true
    try {
      assertThrows(java.util.concurrent.ExecutionException::class.java) { storage.close() }
      assertTrue(Runtime.getRuntime().removeShutdownHook(hook), "failed close must keep the hook")
      storage.close()
      assertEquals(1, api.uploads.get())
      assertEquals(0, api.descriptionUpdates.get())
    } finally {
      Runtime.getRuntime().removeShutdownHook(hook)
      // Existing failed-close behavior leaves the executor alive; clean up the fixture explicitly.
      val executor = storage.javaClass.getDeclaredField("atomicExecutor").apply { isAccessible = true }
        .get(storage) as java.util.concurrent.ExecutorService
      executor.shutdownNow()
    }
  }

  @Test
  fun `JVM shutdown still persists an unclosed storage without a hook removal error`() = FileApiFixture(mapOf("key" to "value-file")).use { api ->
    val java = Path.of(System.getProperty("java.home"), "bin", "java").toString()
    val child = ProcessBuilder(
      java, "-cp", System.getProperty("test.runtime.classpath"),
      ShutdownPersistenceProbe::class.java.name, api.apiUrl
    ).redirectErrorStream(true).start()
    try {
      assertTrue(child.waitFor(20, SECONDS), "shutdown subprocess did not finish")
      val output = child.inputStream.bufferedReader().readText()
      assertEquals(0, child.exitValue(), output)
      assertFalse(output.contains("Exception"), output)
      assertEquals(1, api.uploads.get())
      assertEquals(1, api.descriptionUpdates.get())
    } finally {
      if (child.isAlive) child.destroyForcibly()
    }
  }
}

/** Child JVM exits normally with an unclosed storage; all traffic goes to the parent's loopback fixture. */
object ShutdownPersistenceProbe {
  @JvmStatic
  fun main(args: Array<String>) {
    TelegramStorage(
      bot { token = "local"; apiUrl = args.single() },
      ChatId.fromId(1), String.serializer(), String.serializer()
    )
  }
}
