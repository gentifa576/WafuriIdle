package com.wafuri.idle.application.config

import io.smallrye.config.ConfigMapping
import java.time.Duration

@ConfigMapping(prefix = "cluster")
interface ClusterConfig {
  fun local(): Local

  fun aws(): Aws

  fun discovery(): Discovery

  fun notify(): Notify

  interface Local {
    fun instanceId(): String

    fun internalBaseUrl(): String
  }

  interface Aws {
    fun metadataBaseUrl(): String

    fun metadataTokenTtlSeconds(): Int

    fun requestTimeout(): Duration
  }

  interface Discovery {
    fun heartbeatInterval(): Duration

    fun staleAfter(): Duration
  }

  interface Notify {
    fun requestTimeout(): Duration

    fun maxAttempts(): Int
  }
}
