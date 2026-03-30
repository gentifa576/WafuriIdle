package com.wafuri.idle.application.service.auth

import com.wafuri.idle.application.exception.AuthenticationException
import com.wafuri.idle.application.exception.ValidationException
import com.wafuri.idle.application.port.out.AuthAccountRepository
import com.wafuri.idle.application.port.out.Repository
import com.wafuri.idle.application.service.player.PlayerService
import com.wafuri.idle.domain.model.AuthAccount
import com.wafuri.idle.domain.model.AuthScope
import com.wafuri.idle.domain.model.Player
import com.wafuri.idle.transport.websocket.PlayerWebSocketRegistry
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import org.eclipse.microprofile.jwt.JsonWebToken
import java.util.UUID

@ApplicationScoped
class AuthService(
  private val authAccountRepository: AuthAccountRepository,
  private val playerRepository: Repository<Player, UUID>,
  private val playerService: PlayerService,
  private val passwordHashService: PasswordHashService,
  private val jwtTokenService: JwtTokenService,
  private val authSessionService: AuthSessionService,
  private val playerWebSocketRegistry: PlayerWebSocketRegistry,
) {
  @Transactional
  fun signup(
    username: String,
    email: String?,
    password: String?,
  ): AuthSessionResult {
    val normalizedUsername = username.trim()
    val normalizedEmail = email?.trim()?.takeIf { it.isNotEmpty() }
    val normalizedPassword = password?.trim()?.takeIf { it.isNotEmpty() }
    if (authAccountRepository.findByUsername(normalizedUsername) != null) {
      throw ValidationException("Username is already taken.")
    }
    if (normalizedEmail != null && authAccountRepository.findByEmail(normalizedEmail) != null) {
      throw ValidationException("Email is already registered.")
    }
    if (normalizedEmail != null && normalizedPassword == null) {
      throw ValidationException("Password is required when email is provided.")
    }

    val player = playerService.provision(normalizedUsername)
    val hashedPassword = passwordHashService.hash(normalizedPassword ?: generateGuestPassword())
    authAccountRepository.save(
      AuthAccount(
        playerId = player.id,
        username = normalizedUsername,
        email = normalizedEmail,
        passwordHash = hashedPassword.hash,
        passwordSalt = hashedPassword.salt,
        role = AuthScope.USER,
      ),
    )

    return createSession(player, guestAccount = normalizedEmail == null, role = AuthScope.USER)
  }

  @Transactional
  fun login(
    username: String?,
    email: String?,
    password: String,
  ): AuthSessionResult {
    val normalizedUsername = username?.trim()?.takeIf { it.isNotEmpty() }
    val normalizedEmail = email?.trim()?.takeIf { it.isNotEmpty() }
    if ((normalizedUsername == null) == (normalizedEmail == null)) {
      throw ValidationException("Provide exactly one of username or email.")
    }

    val account =
      normalizedUsername?.let(authAccountRepository::findByUsername)
        ?: normalizedEmail?.let(authAccountRepository::findByEmail)
        ?: throw AuthenticationException("Invalid credentials.")

    if (!passwordHashService.verify(password, account.passwordHash, account.passwordSalt)) {
      throw AuthenticationException("Invalid credentials.")
    }

    val player = playerRepository.findById(account.playerId) ?: throw AuthenticationException("Account player was not found.")
    return createSession(player, account.guestAccount, account.role)
  }

  private fun createSession(
    player: Player,
    guestAccount: Boolean,
    role: AuthScope,
  ): AuthSessionResult {
    val token = jwtTokenService.mint(player.id, player.name, guestAccount, role)
    return token.copy(player = player)
  }

  @Transactional
  fun logout(jwt: JsonWebToken) {
    authSessionService.revoke(jwt)
    UUID.fromString(jwt.subject).let(playerWebSocketRegistry::closeSessions)
  }

  private fun generateGuestPassword(): String = UUID.randomUUID().toString() + UUID.randomUUID().toString()
}
