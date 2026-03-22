package com.wafuri.idle.tests.transport.websocket

import com.fasterxml.jackson.databind.ObjectMapper
import com.wafuri.idle.application.model.EventType
import com.wafuri.idle.application.model.PlayerStateMessage
import com.wafuri.idle.application.port.out.CombatStateRepository
import com.wafuri.idle.domain.model.CombatStatus
import com.wafuri.idle.transport.websocket.PlayerWebSocketRegistry
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.quarkus.test.common.http.TestHTTPResource
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import jakarta.inject.Inject
import jakarta.websocket.ClientEndpoint
import jakarta.websocket.ContainerProvider
import jakarta.websocket.OnMessage
import jakarta.websocket.OnOpen
import jakarta.websocket.Session
import org.junit.jupiter.api.Test
import java.net.URI
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

  @field:TestHTTPResource("/ws/player")
  lateinit var wsBaseUri: URI

  @Test
  fun `player scoped websocket accepts authenticated connections`() {
    val signupResponse =
      given()
        .contentType("application/json")
        .body("""{"name":"SocketUser","email":null,"password":null}""")
        .post("/auth/signup")
        .then()
        .statusCode(201)
        .extract()
    val playerId = signupResponse.path<String>("player.id")
    val token = signupResponse.path<String>("sessionToken")

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
    val signupResponse =
      given()
        .contentType("application/json")
        .body("""{"name":"SocketSyncUser","email":null,"password":null}""")
        .post("/auth/signup")
        .then()
        .statusCode(201)
        .extract()
    val playerId = signupResponse.path<String>("player.id")
    val token = signupResponse.path<String>("sessionToken")
    val teamId =
      given()
        .header("Authorization", "Bearer $token")
        .get("/players/$playerId/teams")
        .then()
        .statusCode(200)
        .extract()
        .path<String>("[0].id")

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
      playerWebSocketRegistry.activePlayerIds().map { it.toString() }.toSet() shouldBe setOf(playerId)

      val initialMessage = collector.messages.poll(5, TimeUnit.SECONDS)
      initialMessage.shouldNotBeNull()
      val playerState = objectMapper.readValue(initialMessage, PlayerStateMessage::class.java)
      playerState.type shouldBe EventType.PLAYER_STATE_SYNC
      playerState.playerId.toString() shouldBe playerId

      session.asyncRemote.sendText("""{"type":"START_COMBAT"}""")

      waitForCombatState(playerId).status shouldBe CombatStatus.FIGHTING
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
