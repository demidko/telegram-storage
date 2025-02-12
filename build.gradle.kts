@file:Suppress("VulnerableLibrariesLocal", "SpellCheckingInspection")

import org.gradle.api.JavaVersion.VERSION_21
import org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21


repositories {
  mavenCentral()
  maven("https://jitpack.io")
}
plugins {
  `java-library`
  `maven-publish`
  kotlin("jvm") version "2.1.20-Beta2"
  kotlin("plugin.serialization") version "2.1.20-Beta2"
}
dependencies {
  api("io.github.kotlin-telegram-bot.kotlin-telegram-bot:telegram:6.3.0")
  api("org.jetbrains.kotlinx:kotlinx-serialization-cbor:1.8.0")
  implementation("com.squareup.retrofit2:retrofit:2.11.0")
  testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
  testImplementation("com.google.truth:truth:1.4.4")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
}
kotlin {
  compilerOptions {
    compilerOptions.optIn.add("kotlinx.serialization.ExperimentalSerializationApi")
  }
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

