import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

repositories {
  mavenCentral()
  maven("https://jitpack.io")
}
plugins {
  `java-library`
  `maven-publish`
  kotlin("jvm") version "1.8.0-RC2"
  kotlin("plugin.serialization") version "1.8.0-RC2"
}
dependencies {
  api("io.github.kotlin-telegram-bot.kotlin-telegram-bot:telegram:6.0.7")
  api("org.jetbrains.kotlinx:kotlinx-serialization-cbor:1.4.1")
  testImplementation("org.junit.jupiter:junit-jupiter:5.9.0")
  testImplementation("com.willowtreeapps.assertk:assertk-jvm:0.25")
  testImplementation("io.mockk:mockk:1.13.2")
}
tasks.withType<KotlinCompile>  {
  kotlinOptions.freeCompilerArgs += "-Xopt-in=kotlinx.serialization.ExperimentalSerializationApi"
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

