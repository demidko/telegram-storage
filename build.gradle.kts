@file:Suppress("VulnerableLibrariesLocal", "SpellCheckingInspection")

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

repositories {
  mavenCentral()
  maven("https://jitpack.io")
}
plugins {
  `java-library`
  `maven-publish`
  kotlin("jvm") version "1.8.0"
  kotlin("plugin.serialization") version "1.8.0"
}
dependencies {
  api("io.github.kotlin-telegram-bot.kotlin-telegram-bot:telegram:6.2.0")
  api("org.jetbrains.kotlinx:kotlinx-serialization-cbor:1.6.0")
  testImplementation("org.junit.jupiter:junit-jupiter:5.9.0")
  implementation("com.squareup.retrofit2:retrofit:2.11.0")
  testImplementation("com.willowtreeapps.assertk:assertk-jvm:0.25")
  testImplementation("io.mockk:mockk:1.13.2")
}
tasks.withType<KotlinCompile>  {
  kotlinOptions.freeCompilerArgs += "-opt-in=kotlinx.serialization.ExperimentalSerializationApi"
}
tasks.test {
  useJUnitPlatform()
}
publishing {
  publications {
    create<MavenPublication>("telegram-storage") {
      from(components["java"])
    }
  }
}

