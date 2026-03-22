package com.wafuri.idle.application.model

import java.time.Instant

data class ClusterNode(
  val instanceId: String,
  val internalBaseUrl: String,
  val lastHeartbeatAt: Instant,
)
