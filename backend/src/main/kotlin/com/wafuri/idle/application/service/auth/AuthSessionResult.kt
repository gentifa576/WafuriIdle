package com.wafuri.idle.application.service.auth

import com.wafuri.idle.domain.model.Player
import java.time.Instant

data class AuthSessionResult(
  val player: Player?,
  val sessionToken: String,
  val sessionExpiresAt: Instant,
  val guestAccount: Boolean,
)
