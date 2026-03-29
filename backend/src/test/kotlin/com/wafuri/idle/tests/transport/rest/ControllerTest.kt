package com.wafuri.idle.tests.transport.rest

import com.wafuri.idle.application.service.auth.JwtTokenService
import com.wafuri.idle.application.service.inventory.InventoryService
import com.wafuri.idle.application.service.player.ProgressionService
import com.wafuri.idle.application.service.team.TeamService
import com.wafuri.idle.domain.model.EquipmentSlot
import com.wafuri.idle.domain.model.Rarity
import com.wafuri.idle.persistence.runtime.LocalPlayerStateWorkQueue
import io.kotest.matchers.collections.shouldContain
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import io.restassured.specification.RequestSpecification
import jakarta.inject.Inject
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.Test
import java.util.UUID

@QuarkusTest
class ControllerTest {
  @Inject
  lateinit var inventoryService: InventoryService

  @Inject
  lateinit var teamService: TeamService

  @Inject
  lateinit var progressionService: ProgressionService

  @Inject
  lateinit var jwtTokenService: JwtTokenService

  @Inject
  lateinit var localPlayerStateWorkQueue: LocalPlayerStateWorkQueue

  private fun signup(
    name: String,
    email: String? = null,
    password: String? = "secret-123",
  ): Pair<String, String> {
    val response =
      given()
        .contentType("application/json")
        .body("""{"name":"$name","email":${email?.let { "\"$it\"" } ?: "null"},"password":${password?.let { "\"$it\"" } ?: "null"}}""")
        .post("/auth/signup")
        .then()
        .statusCode(201)
        .extract()

    return response.path<String>("player.id") to response.path<String>("sessionToken")
  }

  private fun auth(token: String): RequestSpecification =
    given()
      .header("Authorization", "Bearer $token")

  private fun firstTeamId(token: String): String =
    auth(token)
      .get("/players/${playerIdFromToken(token)}/teams")
      .then()
      .statusCode(200)
      .extract()
      .path<List<String>>("id")
      .first()

  private fun playerIdFromToken(token: String): String {
    val tokenParts = token.split('.')
    val encodedPayload = tokenParts.getOrNull(1) ?: error("Unable to decode player id from JWT.")
    val decoder = java.util.Base64.getUrlDecoder()
    val decodedPayload = decoder.decode(encodedPayload)
    val payload = decodedPayload.toString(Charsets.UTF_8)

    return "\"sub\":\"([^\"]+)\"".toRegex().find(payload)?.groupValues?.get(1)
      ?: error("Unable to decode player id from JWT.")
  }

  @Test
  fun `auth endpoints signup login and fetch current player`() {
    val (playerId, token) = signup("Alice", "alice@example.com")

    auth(token)
      .get("/players/$playerId")
      .then()
      .statusCode(200)
      .body("name", equalTo("Alice"))
      .body("experience", equalTo(0))
      .body("level", equalTo(1))
      .body("gold", equalTo(0))
      .body("essence", equalTo(0))
      .body("ownedCharacterKeys.size()", equalTo(0))
      .body("activeTeamId", nullValue())

    given()
      .contentType("application/json")
      .body("""{"name":"Alice","password":"secret-123"}""")
      .post("/auth/login")
      .then()
      .statusCode(200)
      .body("player.name", equalTo("Alice"))
      .body("player.experience", equalTo(0))
      .body("player.level", equalTo(1))
      .body("player.gold", equalTo(0))
      .body("player.essence", equalTo(0))
      .body("guestAccount", equalTo(false))
  }

  @Test
  fun `zone progress endpoint returns player scoped zone progression`() {
    val (playerId, token) = signup("Gail", password = null)

    auth(token)
      .get("/players/$playerId/zone-progress")
      .then()
      .statusCode(200)
      .body("size()", equalTo(0))
  }

  @Test
  fun `claim starter endpoint grants configured starter to new player`() {
    val (playerId, token) = signup("StarterUser", password = null)

    auth(token)
      .contentType("application/json")
      .body("""{"characterKey":"nimbus"}""")
      .post("/players/$playerId/starter")
      .then()
      .statusCode(204)

    auth(token)
      .get("/players/$playerId")
      .then()
      .statusCode(200)
      .body("ownedCharacterKeys", org.hamcrest.Matchers.contains("nimbus"))
  }

  @Test
  fun `claim starter endpoint rejects players that already own characters`() {
    val (playerId, token) = signup("RepeatStarter", password = null)

    auth(token)
      .contentType("application/json")
      .body("""{"characterKey":"nimbus"}""")
      .post("/players/$playerId/starter")
      .then()
      .statusCode(204)

    auth(token)
      .contentType("application/json")
      .body("""{"characterKey":"vyron"}""")
      .post("/players/$playerId/starter")
      .then()
      .statusCode(400)
      .body("message", equalTo("Starter choice is only available for players without owned characters."))
  }

  @Test
  fun `character gacha pull spends gold and grants a character or essence`() {
    val (playerId, token) = signup("GachaUser", password = null)
    repeat(20) {
      progressionService.recordKill(UUID.fromString(playerId), "starter-plains")
    }

    auth(token)
      .contentType("application/json")
      .body("""{"characterKey":"nimbus"}""")
      .post("/players/$playerId/starter")
      .then()
      .statusCode(204)

    repeat(2) {
      auth(token)
        .noContentType()
        .post("/players/$playerId/gacha/characters/pull")
        .then()
        .statusCode(200)
    }

    auth(token)
      .get("/players/$playerId")
      .then()
      .statusCode(200)
      .body("gold", equalTo(0))
      .body("essence", equalTo(15))
      .body("ownedCharacterKeys.size()", equalTo(2))
      .body("ownedCharacterKeys", org.hamcrest.Matchers.hasItem("nimbus"))
  }

  @Test
  fun `inventory endpoint returns owned items`() {
    val (playerId, token) = signup("Bob", password = null)

    inventoryService.addGeneratedItem(UUID.fromString(playerId), "sword_0001", Rarity.COMMON)

    auth(token)
      .get("/players/$playerId/inventory")
      .then()
      .statusCode(200)
      .body("size()", equalTo(1))
      .body("[0].playerId", equalTo(playerId))
  }

  @Test
  fun `equip and unequip endpoints return dto state`() {
    val characterKey = "nimbus"
    val (playerId, token) = signup("Cara", password = null)
    auth(token)
      .contentType("application/json")
      .body("""{"characterKey":"$characterKey"}""")
      .post("/players/$playerId/starter")
      .then()
      .statusCode(204)
    val teamId = firstTeamId(token)
    teamService.assignCharacter(UUID.fromString(playerId), UUID.fromString(teamId), 1, characterKey)
    val inventoryItemId =
      inventoryService.addGeneratedItem(UUID.fromString(playerId), "sword_0001", Rarity.COMMON).id.toString()

    auth(token)
      .contentType("application/json")
      .body("""{"inventoryItemId":"$inventoryItemId","slot":"${EquipmentSlot.WEAPON}"}""")
      .post("/teams/$teamId/slots/1/equip")
      .then()
      .statusCode(204)

    auth(token)
      .contentType("application/json")
      .body("""{"slot":"${EquipmentSlot.WEAPON}"}""")
      .post("/teams/$teamId/slots/1/unequip")
      .then()
      .statusCode(204)
  }

  @Test
  fun `character templates endpoint returns static templates`() {
    given()
      .get("/characters/templates")
      .then()
      .statusCode(200)
      .body("size()", equalTo(432))
      .body("key", org.hamcrest.Matchers.hasItem("vagner"))
      .body("find { it.key == 'vagner' }.tags.size()", equalTo(5))
      .body("find { it.key == 'vagner' }.skill.key", equalTo("prominence-blaze"))
  }

  @Test
  fun `starter templates endpoint returns configured starter choices`() {
    given()
      .get("/characters/starters")
      .then()
      .statusCode(200)
      .body("size()", equalTo(3))
      .body("key", org.hamcrest.Matchers.contains("nimbus", "inaho", "vyron"))
  }

  @Test
  fun `assign character endpoint rejects duplicate team member`() {
    val (playerId, token) = signup("Dora", password = null)
    val characterKey = "nimbus"
    auth(token)
      .contentType("application/json")
      .body("""{"characterKey":"$characterKey"}""")
      .post("/players/$playerId/starter")
      .then()
      .statusCode(204)
    val teamId = firstTeamId(token)
    teamService.assignCharacter(UUID.fromString(playerId), UUID.fromString(teamId), 1, characterKey)

    auth(token)
      .post("/teams/$teamId/slots/2/characters/$characterKey")
      .then()
      .statusCode(400)
      .body("message", equalTo("Character is already on the team."))
  }

  @Test
  fun `assign character endpoint adds owned character to team`() {
    val (playerId, token) = signup("Eli", password = null)
    val teamId = firstTeamId(token)
    val characterKey = "nimbus"
    auth(token)
      .contentType("application/json")
      .body("""{"characterKey":"$characterKey"}""")
      .post("/players/$playerId/starter")
      .then()
      .statusCode(204)

    auth(token)
      .post("/teams/$teamId/slots/1/characters/$characterKey")
      .then()
      .statusCode(200)
      .body("id", equalTo(teamId))
      .body("slots[0].characterKey", equalTo(characterKey))

    auth(token)
      .get("/players/$playerId/teams")
      .then()
      .statusCode(200)
      .body("[0].slots[0].characterKey", equalTo(characterKey))
  }

  @Test
  fun `player combat start rest endpoint is removed`() {
    val (_, token) = signup("Finn", password = null)

    auth(token)
      .post("/players/${playerIdFromToken(token)}/combat/start")
      .then()
      .statusCode(404)
  }

  @Test
  fun `internal dirty endpoint only accepts internal node tokens`() {
    val playerId = UUID.randomUUID()
    val userToken = signup("Ivy", password = null).second
    val internalToken = jwtTokenService.mintInternalNode("node-a")

    given()
      .header("Authorization", "Bearer $userToken")
      .post("/internal/players/$playerId/dirty")
      .then()
      .statusCode(403)

    given()
      .header("Authorization", "Bearer $internalToken")
      .post("/internal/players/$playerId/dirty")
      .then()
      .statusCode(202)

    localPlayerStateWorkQueue.drainDirtyPlayerIds() shouldContain playerId
  }
}
