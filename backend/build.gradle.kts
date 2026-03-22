import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.concurrent.TimeUnit

plugins {
  id("io.quarkus") version "3.31.3"
  id("org.jlleitschuh.gradle.ktlint") version "12.2.0"
  kotlin("jvm") version "2.3.10"
  kotlin("plugin.allopen") version "2.3.10"
  kotlin("plugin.jpa") version "2.3.10"
}

group = "com.wafuri"
version = "0.1.0-SNAPSHOT"

repositories {
  mavenCentral()
}

dependencies {
  val kotestVersion = "6.0.0"
  val mockkVersion = "1.13.17"

  implementation(
    enforcedPlatform(
      "${property("quarkusPlatformGroupId")}:${property("quarkusPlatformArtifactId")}:${property("quarkusPlatformVersion")}",
    ),
  )
  implementation("io.quarkus:quarkus-arc")
  implementation("io.quarkus:quarkus-config-yaml")
  implementation("io.quarkus:quarkus-kotlin")
  implementation("io.quarkus:quarkus-rest-jackson")
  implementation("io.quarkus:quarkus-websockets")
  implementation("io.quarkus:quarkus-hibernate-orm")
  implementation("io.quarkus:quarkus-jdbc-postgresql")
  implementation("io.quarkus:quarkus-jdbc-h2")
  implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

  testImplementation("io.quarkus:quarkus-junit5")
  testImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")
  testImplementation("io.kotest:kotest-assertions-core:$kotestVersion")
  testImplementation("io.mockk:mockk:$mockkVersion")
  testImplementation("io.rest-assured:rest-assured")
  testImplementation("org.glassfish.tyrus.bundles:tyrus-standalone-client:2.1.5")
}

java {
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(25))
  }
  sourceCompatibility = JavaVersion.toVersion(25)
  targetCompatibility = JavaVersion.toVersion(25)
}

kotlin {
  jvmToolchain(25)

  compilerOptions {
    freeCompilerArgs.add("-Xjsr305=strict")
    jvmTarget.set(JvmTarget.fromTarget("25"))
  }
}

allOpen {
  annotation("jakarta.enterprise.context.ApplicationScoped")
  annotation("jakarta.persistence.Entity")
  annotation("jakarta.ws.rs.Path")
  annotation("jakarta.websocket.server.ServerEndpoint")
}

ktlint {
  version.set("1.3.1")
  verbose.set(true)
  outputToConsole.set(true)
}

tasks.check {
  dependsOn(tasks.ktlintCheck)
}

tasks.withType<Test> {
  systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
  javaLauncher.set(
    javaToolchains.launcherFor {
      languageVersion.set(JavaLanguageVersion.of(25))
    },
  )
  useJUnitPlatform()
}

val serverPidFile = layout.buildDirectory.file("server/server.pid")
val serverLogFile = layout.buildDirectory.file("server/server.log")

tasks.register("runServer") {
  group = "application"
  description = "Starts Quarkus dev mode in the background for local use."

  doLast {
    val pidFile = serverPidFile.get().asFile
    val logFile = serverLogFile.get().asFile
    val gradleHome = project.rootDir.resolve(".gradle-user-home").absolutePath
    pidFile.parentFile.mkdirs()
    logFile.parentFile.mkdirs()

    if (pidFile.exists()) {
      val existingPid = pidFile.readText().trim().toLongOrNull()
      if (existingPid != null) {
        val existingProcess = ProcessHandle.of(existingPid).orElse(null)
        if (existingProcess?.isAlive == true) {
          logger.lifecycle("Server is already running with PID $existingPid")
          return@doLast
        }
      }
      pidFile.delete()
    }
    logFile.writeText("")

    val process =
      ProcessBuilder(
        project.rootDir.resolve("gradlew").absolutePath,
        "--no-daemon",
        "-Dquarkus.analytics.disabled=true",
        "quarkusDev",
      ).directory(project.projectDir)
        .redirectErrorStream(true)
        .redirectOutput(ProcessBuilder.Redirect.appendTo(logFile))
        .also { builder ->
          builder.environment()["GRADLE_USER_HOME"] = gradleHome
          builder.environment()["QUARKUS_ANALYTICS_DISABLED"] = "true"
        }.start()

    pidFile.writeText(process.pid().toString())
    logger.lifecycle("Server started with PID ${process.pid()}")
    logger.lifecycle("Log file: ${logFile.absolutePath}")
  }
}

tasks.register("stopServer") {
  group = "application"
  description = "Stops the background Quarkus server started by runServer."

  doLast {
    val pidFile = serverPidFile.get().asFile
    if (!pidFile.exists()) {
      logger.lifecycle("No running server found.")
      return@doLast
    }

    val pid = pidFile.readText().trim().toLongOrNull()
    if (pid == null) {
      pidFile.delete()
      throw GradleException("Invalid PID file at ${pidFile.absolutePath}")
    }

    val process = ProcessHandle.of(pid).orElse(null)
    if (process == null || !process.isAlive) {
      pidFile.delete()
      logger.lifecycle("Server process $pid is not running.")
      return@doLast
    }

    process.destroy()
    if (!process.onExit().get(5, TimeUnit.SECONDS).isAlive) {
      pidFile.delete()
      logger.lifecycle("Server stopped.")
      return@doLast
    }

    process.destroyForcibly()
    process.onExit().get(5, TimeUnit.SECONDS)
    pidFile.delete()
    logger.lifecycle("Server stopped forcefully.")
  }
}
