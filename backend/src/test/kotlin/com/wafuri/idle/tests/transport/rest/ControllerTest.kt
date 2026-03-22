package com.wafuri.idle.tests.transport.rest

import com.wafuri.idle.application.service.inventory.InventoryService
import com.wafuri.idle.application.service.team.TeamService
import com.wafuri.idle.domain.model.EquipmentSlot
import com.wafuri.idle.domain.model.ItemType
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
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

  private fun firstTeamId(playerId: String): String =
    given()
      .get("/players/$playerId/teams")
      .then()
      .statusCode(200)
      .extract()
      .path<List<String>>("id")
      .first()

  @Test
  fun `player endpoints create and fetch player`() {
    val playerId =
      given()
        .contentType("application/json")
        .body("""{"name":"Alice"}""")
        .post("/players")
        .then()
        .statusCode(201)
        .body("name", equalTo("Alice"))
        .body("ownedCharacterKeys[0]", equalTo("warrior"))
        .body("activeTeamId", nullValue())
        .extract()
        .path<String>("id")

    given()
      .get("/players/$playerId")
      .then()
      .statusCode(200)
      .body("id", equalTo(playerId))
      .body("name", equalTo("Alice"))

    given()
      .get("/players/$playerId/teams")
      .then()
      .statusCode(200)
      .body("size()", equalTo(3))
      .body("[0].playerId", equalTo(playerId))
  }

  @Test
  fun `inventory endpoint returns owned items`() {
    val playerId =
      given()
        .contentType("application/json")
        .body("""{"name":"Bob"}""")
        .post("/players")
        .then()
        .statusCode(201)
        .extract()
        .path<String>("id")

    inventoryService.addItem(UUID.fromString(playerId), "Sword", ItemType.WEAPON)

    given()
      .get("/players/$playerId/inventory")
      .then()
      .statusCode(200)
      .body("size()", equalTo(1))
      .body("[0].playerId", equalTo(playerId))
  }

  @Test
  fun `equip and unequip endpoints return dto state`() {
    val characterKey = "warrior"
    val playerId =
      given()
        .contentType("application/json")
        .body("""{"name":"Cara"}""")
        .post("/players")
        .then()
        .statusCode(201)
        .extract()
        .path<String>("id")
    val teamId = firstTeamId(playerId)
    teamService.assignCharacter(UUID.fromString(teamId), characterKey)
    val inventoryItemId =
      inventoryService.addItem(UUID.fromString(playerId), "Sword", ItemType.WEAPON).id.toString()

    given()
      .contentType("application/json")
      .body("""{"inventoryItemId":"$inventoryItemId","slot":"${EquipmentSlot.WEAPON}"}""")
      .post("/characters/$characterKey/equip")
      .then()
      .statusCode(204)

    given()
      .contentType("application/json")
      .body("""{"slot":"${EquipmentSlot.WEAPON}"}""")
      .post("/characters/$characterKey/unequip")
      .then()
      .statusCode(204)
  }

  @Test
  fun `character templates endpoint returns static templates`() {
    given()
      .get("/characters/templates")
      .then()
      .statusCode(200)
      .body("size()", equalTo(3))
      .body("[0].strength.base", equalTo(5.0f))
      .body("[0].strength.increment", equalTo(0.6f))
      .body("[0].skillRefs.size()", equalTo(0))
  }

  @Test
  fun `assign character endpoint rejects duplicate team member`() {
    val playerId =
      given()
        .contentType("application/json")
        .body("""{"name":"Dora"}""")
        .post("/players")
        .then()
        .statusCode(201)
        .extract()
        .path<String>("id")
    val teamId = firstTeamId(playerId)
    val characterKey = "warrior"
    teamService.assignCharacter(UUID.fromString(teamId), characterKey)

    given()
      .post("/teams/$teamId/characters/$characterKey")
      .then()
      .statusCode(400)
      .body("message", equalTo("Character is already on the team."))
  }

  @Test
  fun `assign character endpoint adds owned character to team`() {
    val playerId =
      given()
        .contentType("application/json")
        .body("""{"name":"Eli"}""")
        .post("/players")
        .then()
        .statusCode(201)
        .extract()
        .path<String>("id")
    val teamId = firstTeamId(playerId)
    val characterKey = "warrior"

    given()
      .post("/teams/$teamId/characters/$characterKey")
      .then()
      .statusCode(200)
      .body("id", equalTo(teamId))
      .body("characterKeys.size()", equalTo(1))
      .body("characterKeys[0]", equalTo(characterKey))

    given()
      .get("/players/$playerId/teams")
      .then()
      .statusCode(200)
      .body("[0].characterKeys[0]", equalTo(characterKey))
  }

  @Test
  fun `start combat endpoint returns current combat state`() {
    val playerId =
      given()
        .contentType("application/json")
        .body("""{"name":"Finn"}""")
        .post("/players")
        .then()
        .statusCode(201)
        .extract()
        .path<String>("id")
    val teamId = firstTeamId(playerId)
    val characterKey = "warrior"

    given()
      .post("/teams/$teamId/characters/$characterKey")
      .then()
      .statusCode(200)

    given()
      .post("/players/$playerId/combat/start")
      .then()
      .statusCode(400)
      .body("message", equalTo("Player does not have an active team."))

    given()
      .post("/teams/$teamId/activate")
      .then()
      .statusCode(200)
      .body("id", equalTo(teamId))
      .body("playerId", equalTo(playerId))

    given()
      .post("/players/$playerId/combat/start")
      .then()
      .statusCode(200)
      .body("playerId", equalTo(playerId))
      .body("status", equalTo("FIGHTING"))
      .body("zoneId", equalTo("starter-plains"))
      .body("activeTeamId", equalTo(teamId))
      .body("enemyName", equalTo("Training Dummy"))
      .body("members.size()", equalTo(1))
      .body("members[0].currentHp", equalTo(11.0f))
      .body("members[0].maxHp", equalTo(11.0f))
  }
}
