package com.wafuri.idle.transport.rest.dto

import com.wafuri.idle.application.service.auth.AuthSessionResult
import com.wafuri.idle.domain.model.EquipmentSlot
import com.wafuri.idle.domain.model.Player
import java.util.UUID

data class SignUpRequest(
  val name: String,
  val email: String?,
  val password: String?,
)

data class ClaimStarterRequest(
  val characterKey: String,
)

data class LoginRequest(
  val name: String?,
  val email: String?,
  val password: String,
)

data class EquipItemRequest(
  val inventoryItemId: UUID,
  val slot: EquipmentSlot,
)

data class UnequipItemRequest(
  val slot: EquipmentSlot,
)

data class AuthResponse(
  val player: Player,
  val sessionToken: String,
  val sessionExpiresAt: String,
  val guestAccount: Boolean,
)

fun AuthSessionResult.toResponse(): AuthResponse =
  AuthResponse(
    player = requireNotNull(player) { "Auth responses require a player payload." },
    sessionToken = sessionToken,
    sessionExpiresAt = sessionExpiresAt.toString(),
    guestAccount = guestAccount,
  )
