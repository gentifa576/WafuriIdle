package com.wafuri.idle.tests.transport.websocket

import com.fasterxml.jackson.databind.ObjectMapper
import com.wafuri.idle.application.model.CommandErrorMessage
import com.wafuri.idle.application.model.EventType
import com.wafuri.idle.application.model.PlayerStateMessage
import com.wafuri.idle.application.port.out.CombatStateRepository
import com.wafuri.idle.application.service.character.CharacterTemplateCatalog
import com.wafuri.idle.domain.model.AuthScope
import com.wafuri.idle.domain.model.CombatStatus
import com.wafuri.idle.domain.model.Team
import com.wafuri.idle.tests.support.MessageCollector
import com.wafuri.idle.tests.support.connectPlayerWebSocket
import com.wafuri.idle.tests.support.expectedAuthResponse
import com.wafuri.idle.tests.support.expectedCombatState
import com.wafuri.idle.tests.support.expectedOwnedCharacterSnapshot
import com.wafuri.idle.tests.support.expectedPlayer
import com.wafuri.idle.tests.support.expectedPlayerStateMessage
import com.wafuri.idle.tests.support.expectedPlayerStateSnapshot
import com.wafuri.idle.transport.rest.dto.AuthResponse
import com.wafuri.idle.transport.websocket.PlayerWebSocketRegistry
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank
import io.quarkus.test.common.http.TestHTTPResource
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import io.restassured.common.mapper.TypeRef
import io.smallrye.jwt.build.Jwt
import jakarta.inject.Inject
import jakarta.websocket.Session
import org.junit.jupiter.api.Test
import java.net.URI
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit

@QuarkusTest
class PlayerWebSocketTest {
  @Inject
  lateinit var objectMapper: ObjectMapper

  @Inject
  lateinit var playerWebSocketRegistry: PlayerWebSocketRegistry

  @Inject
  lateinit var combatStateRepository: CombatStateRepository

  @Inject
  lateinit var characterTemplateCatalog: CharacterTemplateCatalog

  @field:TestHTTPResource("/ws/player")
  lateinit var wsBaseUri: URI

  private fun signupGuest(name: String): AuthResponse =
    given()
      .contentType("application/json")
      .body("""{"name":"$name","email":null,"password":null}""")
      .post("/auth/signup")
      .then()
      .statusCode(201)
      .extract()
      .`as`(AuthResponse::class.java)

  private fun authResponse(response: AuthResponse): AuthResponse = response.copy(sessionToken = "", sessionExpiresAt = "")

  private fun teamsResponse(
    token: String,
    playerId: String,
  ): List<Team> =
    given()
      .header("Authorization", "Bearer $token")
      .get("/players/$playerId/teams")
      .then()
      .statusCode(200)
      .extract()
      .`as`(object : TypeRef<List<Team>>() {})

  private fun refreshedSessionToken(
    token: String,
    playerId: String,
  ): String =
    given()
      .header("Authorization", "Bearer $token")
      .get("/players/$playerId")
      .then()
      .statusCode(200)
      .extract()
      .header("X-Session-Token")
      .also { refreshed -> refreshed.shouldNotBeBlank() }

  @Test
  fun `player scoped websocket accepts authenticated connections`() {
    val signupResponse = signupGuest("SocketUser")
    val playerId = signupResponse.player.id.toString()
    val token = signupResponse.sessionToken

    authResponse(signupResponse) shouldBe
      expectedAuthResponse(
        player = expectedPlayer(id = signupResponse.player.id, name = "SocketUser"),
        guestAccount = true,
      )

    val collector = MessageCollector()
    val session: Session = connectPlayerWebSocket(wsBaseUri, playerId, token, collector)
    try {
      collector.opened.await(5, TimeUnit.SECONDS) shouldBe true
      session.isOpen shouldBe true
    } finally {
      session.close()
    }
  }

  @Test
  fun `player websocket receives initial player state and accepts combat start commands`() {
    val signupResponse = signupGuest("SocketSyncUser")
    val playerId = signupResponse.player.id.toString()
    val token = signupResponse.sessionToken
    val teamId = teamsResponse(token, playerId).first().id.toString()
    val starter = characterTemplateCatalog.require("nimbus")

    given()
      .header("Authorization", "Bearer $token")
      .contentType("application/json")
      .body("""{"characterKey":"nimbus"}""")
      .post("/players/$playerId/starter")
      .then()
      .statusCode(204)

    given()
      .header("Authorization", "Bearer $token")
      .contentType("application/json")
      .body(
        """
        {
          "slots":[
            {"position":1,"characterKey":"nimbus","weaponItemId":null,"armorItemId":null,"accessoryItemId":null},
            {"position":2,"characterKey":null,"weaponItemId":null,"armorItemId":null,"accessoryItemId":null},
            {"position":3,"characterKey":null,"weaponItemId":null,"armorItemId":null,"accessoryItemId":null}
          ]
        }
        """.trimIndent(),
      ).post("/teams/$teamId/loadout")
      .then()
      .statusCode(200)

    given()
      .header("Authorization", "Bearer $token")
      .post("/teams/$teamId/activate")
      .then()
      .statusCode(200)

    val collector = MessageCollector()
    val session = connect(collector, playerId, token)
    try {
      collector.opened.await(5, TimeUnit.SECONDS) shouldBe true
      waitForActivePlayer(signupResponse.player.id)
      playerWebSocketRegistry.activePlayerIds() shouldBe setOf(signupResponse.player.id)

      val initialMessage = collector.messages.poll(5, TimeUnit.SECONDS)
      initialMessage.shouldNotBeNull()
      val playerState = objectMapper.readValue(initialMessage, PlayerStateMessage::class.java)
      playerState shouldBe
        expectedPlayerStateMessage(
          playerId = signupResponse.player.id,
          snapshot =
            expectedPlayerStateSnapshot(
              playerId = signupResponse.player.id,
              playerName = "SocketSyncUser",
              ownedCharacters =
                listOf(
                  expectedOwnedCharacterSnapshot(
                    key = starter.key,
                    name = starter.name,
                    level = 1,
                  ),
                ),
              serverTime = playerState.snapshot.serverTime,
            ),
          type = EventType.PLAYER_STATE_SYNC,
        )

      session.asyncRemote.sendText("""{"type":"START_COMBAT"}""")

      val combatState = waitForCombatState(playerId)

      combatState shouldBe
        expectedCombatState(
          playerId = signupResponse.player.id,
          status = CombatStatus.FIGHTING,
          zoneId = "starter-plains",
          activeTeamId = UUID.fromString(teamId),
          enemyName = "Training Dummy",
          enemyHp = 1000f,
          enemyMaxHp = 1000f,
          members = combatState.members,
          pendingDamageMillis = combatState.pendingDamageMillis,
          lastSimulatedAt = combatState.lastSimulatedAt,
        )
    } finally {
      session.close()
    }
  }

  @Test
  fun `player websocket returns command error when combat start is rejected`() {
    val signupResponse = signupGuest("SocketNoActiveTeam")
    val playerId = signupResponse.player.id.toString()
    val token = signupResponse.sessionToken

    val collector = MessageCollector()
    val session = connect(collector, playerId, token)
    try {
      collector.opened.await(5, TimeUnit.SECONDS) shouldBe true
      waitForActivePlayer(signupResponse.player.id)

      collector.messages.poll(5, TimeUnit.SECONDS).shouldNotBeNull()

      session.asyncRemote.sendText("""{"type":"START_COMBAT"}""")

      val errorMessage = collector.messages.poll(5, TimeUnit.SECONDS)
      errorMessage.shouldNotBeNull()

      val commandError = objectMapper.readValue(errorMessage, CommandErrorMessage::class.java)
      commandError.type shouldBe EventType.COMMAND_ERROR
      commandError.playerId shouldBe signupResponse.player.id
      commandError.commandType shouldBe "START_COMBAT"
      commandError.message shouldBe "Player does not have an active team."
      combatStateRepository.findById(signupResponse.player.id).shouldBeNull()
    } finally {
      session.close()
    }
  }

  @Test
  fun `player websocket accepts combat stop commands and clears combat state`() {
    val signupResponse = signupGuest("SocketStopCombat")
    val playerId = signupResponse.player.id.toString()
    val token = signupResponse.sessionToken
    val teamId = teamsResponse(token, playerId).first().id.toString()

    given()
      .header("Authorization", "Bearer $token")
      .contentType("application/json")
      .body("""{"characterKey":"nimbus"}""")
      .post("/players/$playerId/starter")
      .then()
      .statusCode(204)

    given()
      .header("Authorization", "Bearer $token")
      .contentType("application/json")
      .body(
        """
        {
          "slots":[
            {"position":1,"characterKey":"nimbus","weaponItemId":null,"armorItemId":null,"accessoryItemId":null},
            {"position":2,"characterKey":null,"weaponItemId":null,"armorItemId":null,"accessoryItemId":null},
            {"position":3,"characterKey":null,"weaponItemId":null,"armorItemId":null,"accessoryItemId":null}
          ]
        }
        """.trimIndent(),
      ).post("/teams/$teamId/loadout")
      .then()
      .statusCode(200)

    given()
      .header("Authorization", "Bearer $token")
      .post("/teams/$teamId/activate")
      .then()
      .statusCode(200)

    val collector = MessageCollector()
    val session = connect(collector, playerId, token)
    try {
      collector.opened.await(5, TimeUnit.SECONDS) shouldBe true
      waitForActivePlayer(signupResponse.player.id)
      collector.messages.poll(5, TimeUnit.SECONDS).shouldNotBeNull()

      session.asyncRemote.sendText("""{"type":"START_COMBAT"}""")
      collector.messages.poll(5, TimeUnit.SECONDS).shouldNotBeNull()
      waitForCombatState(playerId).status shouldBe CombatStatus.FIGHTING

      session.asyncRemote.sendText("""{"type":"STOP_COMBAT"}""")

      val stopMessage = waitForMessageOfType(collector, EventType.COMBAT_STATE_SYNC)
      stopMessage.shouldNotBeNull()
      val combatMessage = objectMapper.readValue(stopMessage, com.wafuri.idle.application.model.CombatStateMessage::class.java)
      combatMessage.type shouldBe EventType.COMBAT_STATE_SYNC
      combatMessage.snapshot.shouldBeNull()

      combatStateRepository.findById(signupResponse.player.id)?.status shouldBe CombatStatus.IDLE
    } finally {
      session.close()
    }
  }

  @Test
  fun `player websocket rejects connections for a different player id`() {
    val playerA = signupGuest("SocketOwnerA")
    val playerB = signupGuest("SocketOwnerB")
    val targetPlayerId = playerB.player.id.toString()
    val attackerToken = playerA.sessionToken
    val collector = MessageCollector()
    val attemptedSession = runCatching { connect(collector, targetPlayerId, attackerToken) }.getOrNull()
    try {
      attemptedSession?.let { session ->
        collector.opened.await(5, TimeUnit.SECONDS) shouldBe true
        session.asyncRemote.sendText("""{"type":"START_COMBAT"}""")
      }

      collector.messages.poll(1, TimeUnit.SECONDS).shouldBeNull()
      playerWebSocketRegistry.activePlayerIds() shouldBe emptySet()
      combatStateRepository.findById(UUID.fromString(targetPlayerId)).shouldBeNull()
    } finally {
      attemptedSession?.close()
    }
  }

  @Test
  fun `player websocket rejects connections without a token`() {
    val signupResponse = signupGuest("SocketNoToken")
    assertUnauthorizedSocket(
      playerId = signupResponse.player.id.toString(),
      token = null,
    )
  }

  @Test
  fun `player websocket rejects connections with a malformed token`() {
    val signupResponse = signupGuest("SocketBadToken")
    assertUnauthorizedSocket(
      playerId = signupResponse.player.id.toString(),
      token = "not-a-jwt",
    )
  }

  @Test
  fun `player websocket rejects connections with an expired token`() {
    val signupResponse = signupGuest("SocketExpiredToken")
    val playerId = signupResponse.player.id
    assertUnauthorizedSocket(
      playerId = playerId.toString(),
      token = expiredToken(playerId, "SocketExpiredToken"),
    )
  }

  @Test
  fun `player websocket rejects connections after logout revokes the session`() {
    val signupResponse = signupGuest("SocketLoggedOut")
    val playerId = signupResponse.player.id.toString()
    val refreshedToken = refreshedSessionToken(signupResponse.sessionToken, playerId)
    val collector = MessageCollector()
    val session = connect(collector, playerId, refreshedToken)

    try {
      collector.opened.await(5, TimeUnit.SECONDS) shouldBe true
      waitForActivePlayer(signupResponse.player.id)

      given()
        .header("Authorization", "Bearer $refreshedToken")
        .post("/auth/logout")
        .then()
        .statusCode(204)

      waitForInactivePlayer(signupResponse.player.id)
      assertUnauthorizedSocket(playerId = playerId, token = refreshedToken)
    } finally {
      session.close()
    }
  }

  private fun connect(
    collector: MessageCollector,
    playerId: String,
    token: String?,
  ): Session = connectPlayerWebSocket(wsBaseUri, playerId, token, collector)

  private fun assertUnauthorizedSocket(
    playerId: String,
    token: String?,
  ) {
    val collector = MessageCollector()
    val attemptedSession = runCatching { connect(collector, playerId, token) }.getOrNull()
    try {
      attemptedSession?.let { session ->
        collector.opened.await(5, TimeUnit.SECONDS) shouldBe true
        session.asyncRemote.sendText("""{"type":"START_COMBAT"}""")
      }

      collector.messages.poll(1, TimeUnit.SECONDS).shouldBeNull()
      playerWebSocketRegistry.activePlayerIds() shouldBe emptySet()
      combatStateRepository.findById(UUID.fromString(playerId)).shouldBeNull()
    } finally {
      attemptedSession?.close()
    }
  }

  private fun waitForCombatState(playerId: String): com.wafuri.idle.domain.model.CombatState {
    repeat(25) {
      combatStateRepository.findById(java.util.UUID.fromString(playerId))?.let { return it }
      Thread.sleep(200)
    }
    error("Timed out waiting for combat state for player $playerId.")
  }

  private fun waitForActivePlayer(playerId: UUID) {
    repeat(25) {
      if (playerWebSocketRegistry.activePlayerIds().contains(playerId)) {
        return
      }
      Thread.sleep(200)
    }
    error("Timed out waiting for websocket registration for player $playerId.")
  }

  private fun waitForInactivePlayer(playerId: UUID) {
    repeat(25) {
      if (!playerWebSocketRegistry.activePlayerIds().contains(playerId)) {
        return
      }
      Thread.sleep(200)
    }
    error("Timed out waiting for websocket deregistration for player $playerId.")
  }

  private fun expiredToken(
    playerId: UUID,
    username: String,
  ): String {
    val issuedAt = Instant.now().minusSeconds(3600)
    val expiresAt = issuedAt.plusSeconds(60)
    return Jwt
      .issuer("wafuri-idle")
      .subject(playerId.toString())
      .upn(username)
      .claim("scope", "User")
      .claim("role", AuthScope.USER.name)
      .claim("guestAccount", true)
      .issuedAt(issuedAt)
      .expiresAt(expiresAt)
      .sign()
  }

  private fun waitForMessageOfType(
    collector: MessageCollector,
    eventType: EventType,
    timeoutSeconds: Long = 5,
  ): String? {
    val deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds)
    while (System.nanoTime() < deadlineNanos) {
      val remainingNanos = deadlineNanos - System.nanoTime()
      val message = collector.messages.poll(remainingNanos.coerceAtLeast(1), TimeUnit.NANOSECONDS) ?: return null
      val parsedType = objectMapper.readTree(message).path("type").asText(null)
      if (parsedType == eventType.name) {
        return message
      }
    }
    return null
  }
}
