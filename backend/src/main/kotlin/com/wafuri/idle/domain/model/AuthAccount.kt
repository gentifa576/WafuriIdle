package com.wafuri.idle.domain.model

import java.util.UUID

data class AuthAccount(
  val playerId: UUID,
  val username: String,
  val email: String?,
  val passwordHash: String,
  val passwordSalt: String,
  val role: AuthScope,
) {
  init {
    require(username.isNotBlank()) { "Username must not be blank." }
    require(email == null || email.isNotBlank()) { "Email must not be blank when provided." }
    require(passwordHash.isNotBlank()) { "Password hash must not be blank." }
    require(passwordSalt.isNotBlank()) { "Password salt must not be blank." }
  }

  val guestAccount: Boolean
    get() = email == null
}
