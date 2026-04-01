package com.wafuri.idle.application.service.auth

import com.wafuri.idle.application.config.GameConfig
import com.wafuri.idle.domain.model.AuthScope
import io.smallrye.jwt.build.Jwt
import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.jwt.JsonWebToken
import java.time.Instant
import java.util.UUID

@ApplicationScoped
class JwtTokenService(
  private val gameConfig: GameConfig,
) {
  fun mint(
    playerId: UUID,
    username: String,
    guestAccount: Boolean,
    role: AuthScope,
    sessionId: UUID = UUID.randomUUID(),
  ): AuthSessionResult {
    val issuedAt = Instant.now()
    val expiresAt = issuedAt.plus(gameConfig.auth().sessionDuration())
    val sessionToken =
      Jwt
        .issuer(gameConfig.auth().issuer())
        .subject(playerId.toString())
        .upn(username)
        .claim("scope", scopesFor(role).joinToString(" "))
        .claim("sid", sessionId.toString())
        .claim("role", role.name)
        .claim("guestAccount", guestAccount)
        .issuedAt(issuedAt)
        .expiresAt(expiresAt)
        .sign()
    return AuthSessionResult(
      null,
      sessionToken,
      expiresAt,
      guestAccount,
    )
  }

  fun refresh(jwt: JsonWebToken): RefreshedToken =
    mint(
      UUID.fromString(jwt.subject),
      jwt.name,
      jwt.getClaim<Any>("guestAccount") as? Boolean ?: false,
      AuthScope.valueOf(jwt.getClaim<Any>("role") as? String ?: AuthScope.USER.name),
      jwt.getClaim<String>("sid")?.let(UUID::fromString) ?: UUID.randomUUID(),
    ).let { RefreshedToken(it.sessionToken, it.sessionExpiresAt) }

  fun mintInternalNode(instanceId: String): String {
    val issuedAt = Instant.now()
    val expiresAt = issuedAt.plus(gameConfig.auth().sessionDuration())
    return Jwt
      .issuer(gameConfig.auth().issuer())
      .subject(instanceId)
      .upn(instanceId)
      .claim("scope", scopesFor(AuthScope.INTERNAL_NODE).joinToString(" "))
      .claim("role", AuthScope.INTERNAL_NODE.name)
      .claim("guestAccount", false)
      .issuedAt(issuedAt)
      .expiresAt(expiresAt)
      .sign()
  }

  private fun scopesFor(role: AuthScope): Set<String> =
    when (role) {
      AuthScope.USER -> setOf("User")
      AuthScope.DEV -> setOf("User", "Dev")
      AuthScope.ADMIN -> setOf("User", "Dev", "Admin")
      AuthScope.INTERNAL_NODE -> setOf("InternalNode")
    }
}

data class RefreshedToken(
  val token: String,
  val expiresAt: Instant,
)
