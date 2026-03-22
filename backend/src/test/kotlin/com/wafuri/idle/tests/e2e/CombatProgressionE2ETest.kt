package com.wafuri.idle.tests.e2e

import com.wafuri.idle.application.port.out.CombatStateRepository
import com.wafuri.idle.domain.model.CombatStatus
import com.wafuri.idle.tests.support.TestTickWarpService
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.quarkus.test.common.http.TestHTTPResource
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import io.restassured.specification.RequestSpecification
import jakarta.inject.Inject
import jakarta.websocket.ClientEndpoint
import jakarta.websocket.ContainerProvider
import jakarta.websocket.OnMessage
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.Matchers.greaterThan
import org.hamcrest.Matchers.notNullValue
import org.junit.jupiter.api.Test
import java.net.URI
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

@QuarkusTest
class CombatProgressionE2ETest {
  private data class PreparedCombat(
    val playerId: String,
    val token: String,
    val teamId: String,
  )

  @Inject
  lateinit var tickWarpService: TestTickWarpService

  @Inject
  lateinit var combatStateRepository: CombatStateRepository

  @field:TestHTTPResource("/ws/player")
  lateinit var wsBaseUri: URI

  private fun signupGuest(name: String): Pair<String, String> {
    val response =
      given()
        .contentType("application/json")
        .body("""{"name":"$name","email":null,"password":null}""")
        .post("/auth/signup")
        .then()
        .statusCode(201)
        .extract()

    return response.path<String>("player.id") to response.path<String>("sessionToken")
  }

  private fun auth(token: String): RequestSpecification =
    given()
      .header("Authorization", "Bearer $token")

  private fun firstTeamId(
    playerId: String,
    token: String,
  ): String =
    auth(token)
      .get("/players/$playerId/teams")
      .then()
      .statusCode(200)
      .extract()
      .path<List<String>>("id")
      .first()

  private fun prepareStarterCombat(name: String): PreparedCombat {
    val auth = signupGuest(name)
    val playerId = auth.first
    val token = auth.second
    val teamId = firstTeamId(playerId, token)

    auth(token)
      .contentType("application/json")
      .body("""{"characterKey":"nimbus"}""")
      .post("/players/$playerId/starter")
      .then()
      .statusCode(204)

    auth(token)
      .post("/teams/$teamId/slots/1/characters/nimbus")
      .then()
      .statusCode(200)

    auth(token)
      .post("/teams/$teamId/activate")
      .then()
      .statusCode(200)

    startCombatOverWebSocket(playerId, token)

    return PreparedCombat(playerId = playerId, token = token, teamId = teamId)
  }

  @Test
  fun `combat e2e progresses kill rewards through tick warp`() {
    val prepared = prepareStarterCombat("E2EGuest")
    val playerId = prepared.playerId
    val token = prepared.token

    val wonState = tickWarpService.warpCombatUntilStatus(UUID.fromString(playerId), CombatStatus.WON)

    wonState.status shouldBe CombatStatus.WON

    auth(token)
      .get("/players/$playerId")
      .then()
      .statusCode(200)
      .body("experience", equalTo(10))
      .body("level", equalTo(1))

    auth(token)
      .get("/players/$playerId/zone-progress")
      .then()
      .statusCode(200)
      .body("size()", equalTo(1))
      .body("[0].zoneId", equalTo("starter-plains"))
      .body("[0].killCount", equalTo(1))
      .body("[0].level", equalTo(1))
  }

  @Test
  fun `combat e2e grants inventory loot through tick warp`() {
    val prepared = prepareStarterCombat("LootGuest")
    val playerId = prepared.playerId
    val token = prepared.token

    tickWarpService.warpCombatUntilStatus(UUID.fromString(playerId), CombatStatus.WON)

    auth(token)
      .get("/players/$playerId/inventory")
      .then()
      .statusCode(200)
      .body("size()", greaterThan(0))
      .body("[0].id", notNullValue())
      .body("[0].equippedTeamId", equalTo(null))
      .body("[0].equippedPosition", equalTo(null))
  }

  @Test
  fun `combat e2e reaches player level 10 through repeated tick warp wins`() {
    val prepared = prepareStarterCombat("LevelTenGuest")
    val playerId = prepared.playerId
    val token = prepared.token

    tickWarpService.warpCombatWins(UUID.fromString(playerId), wins = 90)

    auth(token)
      .get("/players/$playerId")
      .then()
      .statusCode(200)
      .body("experience", equalTo(900))
      .body("level", equalTo(10))

    auth(token)
      .get("/players/$playerId/zone-progress")
      .then()
      .statusCode(200)
      .body("[0].killCount", equalTo(90))
      .body("[0].level", equalTo(10))

    auth(token)
      .get("/players/$playerId/inventory")
      .then()
      .statusCode(200)
      .body("size()", greaterThan(0))
  }

  private fun startCombatOverWebSocket(
    playerId: String,
    token: String,
  ) {
    val client = ContainerProvider.getWebSocketContainer()
    val collector = MessageCollector()
    val endpointUri =
      URI.create(
        wsBaseUri
          .toString()
          .replaceFirst("http", "ws")
          .trimEnd('/') + "/$playerId?token=$token",
      )
    val session = client.connectToServer(collector, endpointUri)
    try {
      session.asyncRemote.sendText("""{"type":"START_COMBAT"}""")
      collector.messages.poll(5, TimeUnit.SECONDS).shouldNotBeNull()
      waitForCombatState(playerId).status shouldBe CombatStatus.FIGHTING
    } finally {
      session.close()
    }
  }

  private fun waitForCombatState(playerId: String): com.wafuri.idle.domain.model.CombatState {
    repeat(25) {
      combatStateRepository.findById(UUID.fromString(playerId))?.let { return it }
      Thread.sleep(200)
    }
    error("Timed out waiting for combat state for player $playerId.")
  }

  @ClientEndpoint
  class MessageCollector {
    val messages = LinkedBlockingQueue<String>()

    @OnMessage
    fun onMessage(message: String) {
      messages.offer(message)
    }
  }
}
