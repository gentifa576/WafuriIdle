package com.wafuri.idle.tests.e2e

import com.wafuri.idle.application.port.out.CombatStateRepository
import com.wafuri.idle.domain.model.CombatStatus
import com.wafuri.idle.domain.model.InventoryItem
import com.wafuri.idle.domain.model.Player
import com.wafuri.idle.domain.model.PlayerZoneProgress
import com.wafuri.idle.tests.support.TestTickWarpService
import com.wafuri.idle.tests.support.expectedCombatMemberState
import com.wafuri.idle.tests.support.expectedCombatState
import com.wafuri.idle.tests.support.expectedPlayer
import com.wafuri.idle.tests.support.expectedZoneProgress
import com.wafuri.idle.tests.support.swordItem
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.quarkus.test.common.http.TestHTTPResource
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import io.restassured.common.mapper.TypeRef
import io.restassured.specification.RequestSpecification
import jakarta.inject.Inject
import jakarta.websocket.ClientEndpointConfig
import jakarta.websocket.ContainerProvider
import jakarta.websocket.Endpoint
import jakarta.websocket.EndpointConfig
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

  private fun playerResponse(
    token: String,
    playerId: String,
  ): Player =
    auth(token)
      .get("/players/$playerId")
      .then()
      .statusCode(200)
      .extract()
      .`as`(Player::class.java)

  private fun zoneProgressResponse(
    token: String,
    playerId: String,
  ): List<PlayerZoneProgress> =
    auth(token)
      .get("/players/$playerId/zone-progress")
      .then()
      .statusCode(200)
      .extract()
      .`as`(object : TypeRef<List<PlayerZoneProgress>>() {})

  private fun inventoryResponse(
    token: String,
    playerId: String,
  ): List<InventoryItem> =
    auth(token)
      .get("/players/$playerId/inventory")
      .then()
      .statusCode(200)
      .extract()
      .`as`(object : TypeRef<List<InventoryItem>>() {})

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

    startCombatOverWebSocket(playerId, token, teamId)

    return PreparedCombat(playerId = playerId, token = token, teamId = teamId)
  }

  @Test
  fun `combat e2e progresses kill rewards through tick warp`() {
    val prepared = prepareStarterCombat("E2EGuest")
    val playerId = prepared.playerId
    val token = prepared.token

    val wonState = tickWarpService.warpCombatUntilStatus(UUID.fromString(playerId), CombatStatus.WON)

    wonState shouldBe
      expectedCombatState(
        playerId = UUID.fromString(playerId),
        status = CombatStatus.WON,
        zoneId = "starter-plains",
        activeTeamId = UUID.fromString(prepared.teamId),
        enemyName = "Training Dummy",
        enemyHp = 0f,
        enemyMaxHp = 1000f,
        members = listOf(expectedCombatMemberState("nimbus", 13.7f, 9.8f, 2.3000002f, 9.3f)),
        pendingDamageMillis = wonState.pendingDamageMillis,
        lastSimulatedAt = wonState.lastSimulatedAt,
      )

    playerResponse(token, playerId) shouldBe
      expectedPlayer(
        id = UUID.fromString(playerId),
        name = "E2EGuest",
        ownedCharacterKeys = setOf("nimbus"),
        activeTeamId = UUID.fromString(prepared.teamId),
        experience = 10,
        gold = 25,
      )
    zoneProgressResponse(token, playerId) shouldBe
      listOf(
        expectedZoneProgress(
          playerId = UUID.fromString(playerId),
          zoneId = "starter-plains",
          killCount = 1,
          level = 1,
        ),
      )
  }

  @Test
  fun `combat e2e grants inventory loot through tick warp`() {
    val prepared = prepareStarterCombat("LootGuest")
    val playerId = prepared.playerId
    val token = prepared.token

    tickWarpService.warpCombatUntilStatus(UUID.fromString(playerId), CombatStatus.WON)

    normalizeInventory(inventoryResponse(token, playerId)) shouldBe
      listOf(
        normalizedInventoryItem(
          playerId = UUID.fromString(playerId),
        ),
      )
  }

  @Test
  fun `combat e2e revives a wiped team at half hp after the revive delay`() {
    val prepared = prepareStarterCombat("LevelTenGuest")
    val playerId = prepared.playerId
    val token = prepared.token

    tickWarpService.warpCombatUntilStatus(UUID.fromString(playerId), CombatStatus.WON)
    val downState = tickWarpService.warpCombatUntilStatus(UUID.fromString(playerId), CombatStatus.DOWN)
    val revivedState = tickWarpService.warpCombat(UUID.fromString(playerId), java.time.Duration.ofSeconds(30))

    downState shouldBe
      expectedCombatState(
        playerId = UUID.fromString(playerId),
        status = CombatStatus.DOWN,
        zoneId = "starter-plains",
        activeTeamId = UUID.fromString(prepared.teamId),
        enemyName = "Training Dummy",
        enemyHp = 597.22f,
        enemyMaxHp = 1000f,
        members = listOf(expectedCombatMemberState("nimbus", 13.7f, 9.8f, 0f, 9.3f)),
        lastSimulatedAt = downState.lastSimulatedAt,
      )
    revivedState shouldBe
      expectedCombatState(
        playerId = UUID.fromString(playerId),
        status = CombatStatus.FIGHTING,
        zoneId = "starter-plains",
        activeTeamId = UUID.fromString(prepared.teamId),
        enemyName = "Training Dummy",
        enemyHp = 1000f,
        enemyMaxHp = 1000f,
        members = listOf(expectedCombatMemberState("nimbus", 13.7f, 9.8f, 4.65f, 9.3f)),
        lastSimulatedAt = revivedState.lastSimulatedAt,
      )

    playerResponse(token, playerId) shouldBe
      expectedPlayer(
        id = UUID.fromString(playerId),
        name = "LevelTenGuest",
        ownedCharacterKeys = setOf("nimbus"),
        activeTeamId = UUID.fromString(prepared.teamId),
        experience = 10,
        gold = 25,
      )
    zoneProgressResponse(token, playerId) shouldBe
      listOf(
        expectedZoneProgress(
          playerId = UUID.fromString(playerId),
          zoneId = "starter-plains",
          killCount = 1,
          level = 1,
        ),
      )
    normalizeInventory(inventoryResponse(token, playerId)) shouldBe
      listOf(
        normalizedInventoryItem(
          playerId = UUID.fromString(playerId),
        ),
      )
  }

  private fun startCombatOverWebSocket(
    playerId: String,
    token: String,
    teamId: String,
  ) {
    val client = ContainerProvider.getWebSocketContainer()
    val collector = MessageCollector()
    val endpointUri =
      URI.create(
        wsBaseUri
          .toString()
          .replaceFirst("http", "ws")
          .trimEnd('/') + "/$playerId",
      )
    val session = client.connectToServer(clientEndpoint(collector), clientConfig(token), endpointUri)
    try {
      session.asyncRemote.sendText("""{"type":"START_COMBAT"}""")
      collector.messages.poll(5, TimeUnit.SECONDS).shouldNotBeNull()
      val combatState = waitForCombatState(playerId)

      combatState shouldBe
        expectedCombatState(
          playerId = UUID.fromString(playerId),
          status = CombatStatus.FIGHTING,
          zoneId = "starter-plains",
          activeTeamId = UUID.fromString(teamId),
          enemyName = "Training Dummy",
          enemyHp = 1000f,
          enemyMaxHp = 1000f,
          members = listOf(expectedCombatMemberState("nimbus", 13.7f, 9.8f, 9.3f, 9.3f)),
          lastSimulatedAt = combatState.lastSimulatedAt,
        )
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

  private fun clientConfig(token: String): ClientEndpointConfig =
    ClientEndpointConfig
      .Builder
      .create()
      .preferredSubprotocols(
        listOf(
          "bearer-token-carrier",
          "quarkus-http-upgrade#Authorization#Bearer%20$token",
        ),
      ).build()

  private fun clientEndpoint(collector: MessageCollector): Endpoint =
    object : Endpoint() {
      override fun onOpen(
        session: jakarta.websocket.Session,
        config: EndpointConfig,
      ) {
        session.addMessageHandler(String::class.java) { message -> collector.onMessage(message) }
      }
    }

  class MessageCollector {
    val messages = LinkedBlockingQueue<String>()

    fun onMessage(message: String) {
      messages.offer(message)
    }
  }
}

private fun normalizeInventory(items: List<InventoryItem>): List<InventoryItem> =
  items.mapIndexed { index, item ->
    item.copy(id = normalizedInventoryId(index))
  }

private fun normalizedInventoryItem(
  playerId: UUID,
  ordinal: Int = 0,
): InventoryItem =
  InventoryItem(
    id = normalizedInventoryId(ordinal),
    playerId = playerId,
    item = swordItem(),
  )

private fun normalizedInventoryId(index: Int): UUID = UUID.nameUUIDFromBytes("inventory-$index".toByteArray())
