package com.wafuri.idle.domain.model

import java.time.Instant
import java.util.UUID

data class RevokedSession(
  val sessionId: UUID,
  val expiresAt: Instant,
)
