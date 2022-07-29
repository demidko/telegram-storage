repositories {
  mavenCentral()
  maven("https://jitpack.io")
}
plugins {
  `java-library`
  `maven-publish`
  kotlin("jvm") version "1.7.20-Beta"
  kotlin("plugin.serialization") version "1.7.20-Beta"
}
dependencies {
  api("io.github.kotlin-telegram-bot.kotlin-telegram-bot:telegram:6.0.7")
  api("org.jetbrains.kotlinx:kotlinx-serialization-cbor:1.4.0-RC")
  testImplementation("org.junit.jupiter:junit-jupiter:5.9.0")
  testImplementation("com.willowtreeapps.assertk:assertk-jvm:0.25")
  testImplementation("io.mockk:mockk:1.12.5")
}
tasks.compileKotlin {
  kotlinOptions.freeCompilerArgs += "-Xopt-in=kotlinx.serialization.ExperimentalSerializationApi"
}
tasks.compileTestKotlin {
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

