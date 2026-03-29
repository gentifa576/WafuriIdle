package com.wafuri.idle.tests.transport.rest

import com.wafuri.idle.application.config.GameConfig
import com.wafuri.idle.application.model.CharacterPullResult
import com.wafuri.idle.application.service.auth.JwtTokenService
import com.wafuri.idle.application.service.character.CharacterTemplateCatalog
import com.wafuri.idle.application.service.inventory.InventoryService
import com.wafuri.idle.application.service.player.ProgressionService
import com.wafuri.idle.application.service.team.TeamService
import com.wafuri.idle.domain.model.CharacterTemplate
import com.wafuri.idle.domain.model.EquipmentSlot
import com.wafuri.idle.domain.model.InventoryItem
import com.wafuri.idle.domain.model.Player
import com.wafuri.idle.domain.model.PlayerZoneProgress
import com.wafuri.idle.domain.model.Rarity
import com.wafuri.idle.domain.model.Team
import com.wafuri.idle.persistence.runtime.LocalPlayerStateWorkQueue
import com.wafuri.idle.tests.support.expectedAuthResponse
import com.wafuri.idle.tests.support.expectedCharacterPullResult
import com.wafuri.idle.tests.support.expectedErrorResponse
import com.wafuri.idle.tests.support.expectedPlayer
import com.wafuri.idle.transport.rest.dto.AuthResponse
import com.wafuri.idle.transport.rest.dto.ErrorResponse
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import io.restassured.common.mapper.TypeRef
import io.restassured.specification.RequestSpecification
import jakarta.inject.Inject
import org.junit.jupiter.api.Test
import java.util.UUID

@QuarkusTest
class ControllerTest {
  @Inject
  lateinit var inventoryService: InventoryService

  @Inject
  lateinit var teamService: TeamService

  @Inject
  lateinit var characterTemplateCatalog: CharacterTemplateCatalog

  @Inject
  lateinit var gameConfig: GameConfig

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
  ): AuthResponse =
    given()
      .contentType("application/json")
      .body("""{"name":"$name","email":${email?.let { "\"$it\"" } ?: "null"},"password":${password?.let { "\"$it\"" } ?: "null"}}""")
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
    auth(token)
      .get("/players/$playerId/teams")
      .then()
      .statusCode(200)
      .extract()
      .`as`(object : TypeRef<List<Team>>() {})

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

  private fun starterTemplatesResponse(): List<CharacterTemplate> =
    given()
      .get("/characters/starters")
      .then()
      .statusCode(200)
      .extract()
      .`as`(object : TypeRef<List<CharacterTemplate>>() {})

  private fun allTemplatesResponse(): List<CharacterTemplate> =
    given()
      .get("/characters/templates")
      .then()
      .statusCode(200)
      .extract()
      .`as`(object : TypeRef<List<CharacterTemplate>>() {})

  private fun errorResponse(
    specification: RequestSpecification,
    path: String,
    body: String? = null,
  ): ErrorResponse {
    val request =
      if (body == null) {
        specification
      } else {
        specification.contentType("application/json").body(body)
      }
    return request
      .post(path)
      .then()
      .statusCode(400)
      .extract()
      .`as`(ErrorResponse::class.java)
  }

  private fun firstTeamId(token: String): String = teamsResponse(token, playerIdFromToken(token)).first().id.toString()

  @Test
  fun `auth endpoints signup login and fetch current player`() {
    val signupResponse = signup("Alice", "alice@example.com")
    val playerId = signupResponse.player.id.toString()
    val token = signupResponse.sessionToken

    playerResponse(token, playerId) shouldBe expectedPlayer(id = signupResponse.player.id, name = "Alice")
    authResponse(signupResponse) shouldBe
      expectedAuthResponse(
        player = expectedPlayer(id = signupResponse.player.id, name = "Alice"),
        guestAccount = false,
      )

    val loginResponse =
      given()
        .contentType("application/json")
        .body("""{"name":"Alice","password":"secret-123"}""")
        .post("/auth/login")
        .then()
        .statusCode(200)
        .extract()
        .`as`(AuthResponse::class.java)

    authResponse(loginResponse) shouldBe
      expectedAuthResponse(
        player = expectedPlayer(id = signupResponse.player.id, name = "Alice"),
        guestAccount = false,
      )
  }

  private fun auth(token: String): RequestSpecification =
    given()
      .header("Authorization", "Bearer $token")

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
  fun `zone progress endpoint returns player scoped zone progression`() {
    val signupResponse = signup("Gail", password = null)

    zoneProgressResponse(signupResponse.sessionToken, signupResponse.player.id.toString()) shouldBe
      emptyList<PlayerZoneProgress>()
  }

  @Test
  fun `claim starter endpoint grants configured starter to new player`() {
    val signupResponse = signup("StarterUser", password = null)
    val playerId = signupResponse.player.id.toString()
    val token = signupResponse.sessionToken

    auth(token)
      .contentType("application/json")
      .body("""{"characterKey":"nimbus"}""")
      .post("/players/$playerId/starter")
      .then()
      .statusCode(204)

    playerResponse(token, playerId) shouldBe
      expectedPlayer(
        id = signupResponse.player.id,
        name = "StarterUser",
        ownedCharacterKeys = setOf("nimbus"),
      )
  }

  @Test
  fun `claim starter endpoint rejects players that already own characters`() {
    val signupResponse = signup("RepeatStarter", password = null)
    val playerId = signupResponse.player.id.toString()
    val token = signupResponse.sessionToken

    auth(token)
      .contentType("application/json")
      .body("""{"characterKey":"nimbus"}""")
      .post("/players/$playerId/starter")
      .then()
      .statusCode(204)

    errorResponse(
      auth(token),
      "/players/$playerId/starter",
      """{"characterKey":"vyron"}""",
    ) shouldBe
      expectedErrorResponse("Starter choice is only available for players without owned characters.")
  }

  @Test
  fun `character gacha pull spends gold and grants a character or essence`() {
    val signupResponse = signup("GachaUser", password = null)
    val playerId = signupResponse.player.id.toString()
    val token = signupResponse.sessionToken
    repeat(20) {
      progressionService.recordKill(UUID.fromString(playerId), "starter-plains")
    }
    val rolledCharacterKey = characterTemplateCatalog.all().first().key

    auth(token)
      .contentType("application/json")
      .body("""{"characterKey":"nimbus"}""")
      .post("/players/$playerId/starter")
      .then()
      .statusCode(204)

    val firstPull =
      auth(token)
        .noContentType()
        .post("/players/$playerId/gacha/characters/pull")
        .then()
        .statusCode(200)
        .extract()
        .`as`(CharacterPullResult::class.java)
    val secondPull =
      auth(token)
        .noContentType()
        .post("/players/$playerId/gacha/characters/pull")
        .then()
        .statusCode(200)
        .extract()
        .`as`(CharacterPullResult::class.java)

    firstPull shouldBe
      expectedCharacterPullResult(
        player =
          expectedPlayer(
            id = signupResponse.player.id,
            name = "GachaUser",
            ownedCharacterKeys = setOf("nimbus", rolledCharacterKey),
            experience = 200,
            level = 3,
            gold = 250,
          ),
        pulledCharacterKey = rolledCharacterKey,
        grantedCharacterKey = rolledCharacterKey,
        essenceGranted = 0,
      )
    secondPull shouldBe
      expectedCharacterPullResult(
        player =
          expectedPlayer(
            id = signupResponse.player.id,
            name = "GachaUser",
            ownedCharacterKeys = setOf("nimbus", rolledCharacterKey),
            experience = 200,
            level = 3,
            gold = 0,
            essence = 15,
          ),
        pulledCharacterKey = rolledCharacterKey,
        grantedCharacterKey = null,
        essenceGranted = 15,
      )
    playerResponse(token, playerId) shouldBe
      expectedPlayer(
        id = signupResponse.player.id,
        name = "GachaUser",
        ownedCharacterKeys = setOf("nimbus", rolledCharacterKey),
        experience = 200,
        level = 3,
        gold = 0,
        essence = 15,
      )
  }

  @Test
  fun `inventory endpoint returns owned items`() {
    val signupResponse = signup("Bob", password = null)
    val playerId = signupResponse.player.id.toString()
    val token = signupResponse.sessionToken

    val addedItem = inventoryService.addGeneratedItem(UUID.fromString(playerId), "sword_0001", Rarity.COMMON)

    inventoryResponse(token, playerId) shouldBe listOf(addedItem)
  }

  @Test
  fun `equip and unequip endpoints return dto state`() {
    val characterKey = "nimbus"
    val signupResponse = signup("Cara", password = null)
    val playerId = signupResponse.player.id.toString()
    val token = signupResponse.sessionToken
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
    allTemplatesResponse() shouldBe characterTemplateCatalog.all()
  }

  @Test
  fun `starter templates endpoint returns configured starter choices`() {
    starterTemplatesResponse() shouldBe characterTemplateCatalog.requireAll(gameConfig.team().starterChoices())
  }

  @Test
  fun `assign character endpoint rejects duplicate team member`() {
    val signupResponse = signup("Dora", password = null)
    val playerId = signupResponse.player.id.toString()
    val token = signupResponse.sessionToken
    val characterKey = "nimbus"
    auth(token)
      .contentType("application/json")
      .body("""{"characterKey":"$characterKey"}""")
      .post("/players/$playerId/starter")
      .then()
      .statusCode(204)
    val teamId = firstTeamId(token)
    teamService.assignCharacter(UUID.fromString(playerId), UUID.fromString(teamId), 1, characterKey)

    errorResponse(auth(token), "/teams/$teamId/slots/2/characters/$characterKey") shouldBe
      expectedErrorResponse("Character is already on the team.")
  }

  @Test
  fun `assign character endpoint adds owned character to team`() {
    val signupResponse = signup("Eli", password = null)
    val playerId = signupResponse.player.id.toString()
    val token = signupResponse.sessionToken
    val teamId = firstTeamId(token)
    val characterKey = "nimbus"
    auth(token)
      .contentType("application/json")
      .body("""{"characterKey":"$characterKey"}""")
      .post("/players/$playerId/starter")
      .then()
      .statusCode(204)

    val assignedTeam =
      auth(token)
        .post("/teams/$teamId/slots/1/characters/$characterKey")
        .then()
        .statusCode(200)
        .extract()
        .`as`(Team::class.java)

    assignedTeam shouldBe teamService.listByPlayer(UUID.fromString(playerId)).first { it.id.toString() == teamId }
    teamsResponse(token, playerId) shouldBe teamService.listByPlayer(UUID.fromString(playerId))
  }

  @Test
  fun `player combat start rest endpoint is removed`() {
    val token = signup("Finn", password = null).sessionToken

    auth(token)
      .post("/players/${playerIdFromToken(token)}/combat/start")
      .then()
      .statusCode(404)
  }

  @Test
  fun `internal dirty endpoint only accepts internal node tokens`() {
    val playerId = UUID.randomUUID()
    val userToken = signup("Ivy", password = null).sessionToken
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
