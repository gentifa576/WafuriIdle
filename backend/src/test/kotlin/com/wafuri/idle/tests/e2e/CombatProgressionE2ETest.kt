package com.wafuri.idle.tests.e2e

import com.wafuri.idle.application.port.out.CombatStateRepository
import com.wafuri.idle.application.service.scaling.ScalingRule
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

  @Inject
  lateinit var scalingRule: ScalingRule

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
        members = listOf(expectedCombatMemberState("nimbus", 23.975f, 3.1304953f, 452f, 465f)),
        pendingDamageMillis = wonState.pendingDamageMillis,
        pendingRespawnMillis = wonState.pendingRespawnMillis,
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
          killCount = 16,
          level = 2,
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

    tickWarpService.warpCombatWins(UUID.fromString(playerId), wins = 60)
    val downState = tickWarpService.warpCombatUntilStatus(UUID.fromString(playerId), CombatStatus.DOWN, maxSteps = 2_000)
    val revivedState = tickWarpService.warpCombat(UUID.fromString(playerId), java.time.Duration.ofSeconds(30))
    val zoneProgress = zoneProgressResponse(token, playerId).single()
    val expectedEnemyAttack = scalingRule.enemyAttackFor(zoneProgress.level, 1f)
    val expectedEnemyMaxHp = scalingRule.enemyHpFor(zoneProgress.level, 1_000f)

    downState.status shouldBe CombatStatus.DOWN
    downState.playerId shouldBe UUID.fromString(playerId)
    downState.zoneId shouldBe "starter-plains"
    downState.activeTeamId shouldBe UUID.fromString(prepared.teamId)
    downState.enemyName shouldBe "Training Dummy"
    downState.enemyLevel shouldBe zoneProgress.level
    downState.enemyBaseHp shouldBe 1_000f
    downState.enemyAttack shouldBe expectedEnemyAttack
    downState.enemyMaxHp shouldBe expectedEnemyMaxHp
    (downState.enemyHp > 0f) shouldBe true
    (downState.enemyHp < expectedEnemyMaxHp) shouldBe true
    downState.members.size shouldBe 1
    downState.members.single().characterKey shouldBe "nimbus"
    downState.members.single().currentHp shouldBe 0f
    (downState.members.single().maxHp > 0f) shouldBe true
    (downState.members.single().attack > 0f) shouldBe true
    (downState.members.single().hit > 0f) shouldBe true

    revivedState.status shouldBe CombatStatus.FIGHTING
    revivedState.playerId shouldBe UUID.fromString(playerId)
    revivedState.zoneId shouldBe "starter-plains"
    revivedState.activeTeamId shouldBe UUID.fromString(prepared.teamId)
    revivedState.enemyName shouldBe "Training Dummy"
    revivedState.enemyLevel shouldBe zoneProgress.level
    revivedState.enemyBaseHp shouldBe 1_000f
    revivedState.enemyAttack shouldBe expectedEnemyAttack
    revivedState.enemyHp shouldBe expectedEnemyMaxHp
    revivedState.enemyMaxHp shouldBe expectedEnemyMaxHp
    revivedState.members.size shouldBe 1
    revivedState.members.single().characterKey shouldBe "nimbus"
    revivedState.members.single().attack shouldBe downState.members.single().attack
    revivedState.members.single().hit shouldBe downState.members.single().hit
    revivedState.members.single().maxHp shouldBe downState.members.single().maxHp
    (kotlin.math.abs(revivedState.members.single().currentHp - (revivedState.members.single().maxHp / 2f)) < 0.001f) shouldBe true

    val playerSnapshot = playerResponse(token, playerId)

    playerSnapshot shouldBe
      expectedPlayer(
        id = UUID.fromString(playerId),
        name = "LevelTenGuest",
        ownedCharacterKeys = setOf("nimbus"),
        activeTeamId = UUID.fromString(prepared.teamId),
        experience = playerSnapshot.experience,
        level = playerSnapshot.level,
        gold = playerSnapshot.gold,
      )
    listOf(zoneProgress) shouldBe
      listOf(
        expectedZoneProgress(
          playerId = UUID.fromString(playerId),
          zoneId = "starter-plains",
          killCount = zoneProgress.killCount,
          level = zoneProgress.level,
        ),
      )
    val inventory = normalizeInventory(inventoryResponse(token, playerId))
    inventory.isNotEmpty() shouldBe true
    (inventory.size <= playerSnapshot.gold / 25) shouldBe true
    inventory.first().playerId shouldBe UUID.fromString(playerId)
    inventory.last().playerId shouldBe UUID.fromString(playerId)
    inventory.first().itemLevel shouldBe 1
    (inventory.last().itemLevel >= inventory.first().itemLevel) shouldBe true
    inventory.map { it.item.name }.distinct() shouldBe listOf("sword_0001")
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
          members = listOf(expectedCombatMemberState("nimbus", 23.975f, 3.1304953f, 465f, 465f)),
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
  itemLevel: Int = 1,
): InventoryItem =
  InventoryItem(
    id = normalizedInventoryId(ordinal),
    playerId = playerId,
    item = swordItem(),
    itemLevel = itemLevel,
  )

private fun normalizedInventoryId(index: Int): UUID = UUID.nameUUIDFromBytes("inventory-$index".toByteArray())
