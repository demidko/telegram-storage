repositories {
  mavenCentral()
  maven("https://jitpack.io")
}
plugins {
  `java-library`
  `maven-publish`
  kotlin("jvm") version "1.5.31"
  kotlin("plugin.serialization") version "1.5.31"
}
dependencies {
  api("org.jetbrains.kotlinx:kotlinx-serialization-protobuf:1.3.0")
  api("io.github.kotlin-telegram-bot.kotlin-telegram-bot:telegram:6.0.6")
  testImplementation("org.junit.jupiter:junit-jupiter:5.8.1")
  testImplementation("com.willowtreeapps.assertk:assertk-jvm:0.25")
  testImplementation("io.mockk:mockk:1.12.0")
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
    create<MavenPublication>("durov") {
      from(components["java"])
    }
  }
}

