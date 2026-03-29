package com.wafuri.idle.tests.transport.websocket

import com.fasterxml.jackson.databind.ObjectMapper
import com.wafuri.idle.application.model.EventType
import com.wafuri.idle.application.model.PlayerStateMessage
import com.wafuri.idle.application.port.out.CombatStateRepository
import com.wafuri.idle.application.service.character.CharacterTemplateCatalog
import com.wafuri.idle.domain.model.CombatStatus
import com.wafuri.idle.domain.model.Team
import com.wafuri.idle.tests.support.expectedAuthResponse
import com.wafuri.idle.tests.support.expectedCombatMemberState
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
import io.quarkus.test.common.http.TestHTTPResource
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import io.restassured.common.mapper.TypeRef
import jakarta.inject.Inject
import jakarta.websocket.ClientEndpoint
import jakarta.websocket.ContainerProvider
import jakarta.websocket.OnMessage
import jakarta.websocket.OnOpen
import jakarta.websocket.Session
import org.junit.jupiter.api.Test
import java.net.URI
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
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
    val client = ContainerProvider.getWebSocketContainer()
    val endpointUri =
      URI.create(
        wsBaseUri
          .toString()
          .replaceFirst("http", "ws")
          .trimEnd('/') + "/$playerId?token=$token",
      )
    val session: Session = client.connectToServer(collector, endpointUri)
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
      .post("/teams/$teamId/slots/1/characters/nimbus")
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
          members = listOf(expectedCombatMemberState("nimbus", 13.7f, 9.8f, 9.3f, 9.3f)),
          pendingDamageMillis = combatState.pendingDamageMillis,
          lastSimulatedAt = combatState.lastSimulatedAt,
        )
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
    val session = connect(collector, targetPlayerId, attackerToken)
    try {
      collector.opened.await(5, TimeUnit.SECONDS) shouldBe true
      playerWebSocketRegistry.activePlayerIds() shouldBe emptySet()

      session.asyncRemote.sendText("""{"type":"START_COMBAT"}""")

      collector.messages.poll(1, TimeUnit.SECONDS).shouldBeNull()
      combatStateRepository.findById(UUID.fromString(targetPlayerId)).shouldBeNull()
    } finally {
      session.close()
    }
  }

  private fun connect(
    collector: MessageCollector,
    playerId: String,
    token: String,
  ): Session {
    val client = ContainerProvider.getWebSocketContainer()
    val endpointUri =
      URI.create(
        wsBaseUri
          .toString()
          .replaceFirst("http", "ws")
          .trimEnd('/') + "/$playerId?token=$token",
      )
    return client.connectToServer(collector, endpointUri)
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

  @ClientEndpoint
  class MessageCollector {
    val opened = CountDownLatch(1)
    val messages = LinkedBlockingQueue<String>()

    @OnOpen
    fun onOpen() {
      opened.countDown()
    }

    @OnMessage
    fun onMessage(message: String) {
      messages.offer(message)
    }
  }
}
