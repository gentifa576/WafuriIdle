package com.wafuri.idle.application.service.auth

import com.wafuri.idle.application.exception.AuthenticationException
import com.wafuri.idle.application.port.out.RevokedSessionRepository
import com.wafuri.idle.domain.model.RevokedSession
import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.jwt.JsonWebToken
import java.time.Instant
import java.util.UUID

@ApplicationScoped
class AuthSessionService(
  private val revokedSessionRepository: RevokedSessionRepository,
) {
  fun revoke(jwt: JsonWebToken) {
    val sessionId = jwt.sessionId() ?: return
    revokedSessionRepository.save(
      RevokedSession(
        sessionId = sessionId,
        expiresAt = jwt.expiresAtInstant(),
      ),
    )
  }

  fun isRevoked(jwt: JsonWebToken): Boolean {
    val sessionId = jwt.sessionId() ?: return false
    val revoked = revokedSessionRepository.findById(sessionId) ?: return false
    if (!revoked.expiresAt.isAfter(Instant.now())) {
      revokedSessionRepository.delete(sessionId)
      return false
    }
    return true
  }

  fun requireActive(jwt: JsonWebToken) {
    if (isRevoked(jwt)) {
      throw AuthenticationException("Session is no longer active.")
    }
  }
}

private fun JsonWebToken.sessionId(): UUID? =
  getClaim<String>("sid")?.let(UUID::fromString)

private fun JsonWebToken.expiresAtInstant(): Instant {
  val expiration = getClaim<Any>("exp")
  return when (expiration) {
    is Instant -> expiration
    is Number -> Instant.ofEpochSecond(expiration.toLong())
    is String -> Instant.ofEpochSecond(expiration.toLong())
    else -> error("JWT expiration claim was missing or invalid.")
  }
}
