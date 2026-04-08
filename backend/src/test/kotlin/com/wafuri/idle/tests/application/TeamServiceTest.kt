package com.wafuri.idle.tests.application

import com.wafuri.idle.application.exception.ValidationException
import com.wafuri.idle.application.port.out.CombatStateRepository
import com.wafuri.idle.application.port.out.InventoryRepository
import com.wafuri.idle.application.port.out.PlayerStateWorkQueue
import com.wafuri.idle.application.port.out.Repository
import com.wafuri.idle.application.port.out.TeamRepository
import com.wafuri.idle.application.service.character.CharacterTemplateCatalog
import com.wafuri.idle.application.service.combat.CombatStatService
import com.wafuri.idle.application.service.team.TeamService
import com.wafuri.idle.domain.model.CombatStatus
import com.wafuri.idle.domain.model.InventoryItem
import com.wafuri.idle.domain.model.Player
import com.wafuri.idle.domain.model.Team
import com.wafuri.idle.domain.model.TeamMemberSlot
import com.wafuri.idle.tests.support.expectedInventoryItem
import com.wafuri.idle.tests.support.expectedPlayer
import com.wafuri.idle.tests.support.expectedSingleMemberCombatState
import com.wafuri.idle.tests.support.expectedTeam
import com.wafuri.idle.tests.support.expectedValidationException
import com.wafuri.idle.tests.support.shouldMatchExpected
import com.wafuri.idle.tests.support.swordItem
import com.wafuri.idle.tests.support.warriorTemplate
import com.wafuri.idle.transport.rest.dto.TeamSlotLoadoutRequest
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import java.util.UUID

class TeamServiceTest : StringSpec() {
  private lateinit var playerRepository: Repository<Player, UUID>
  private lateinit var teamRepository: TeamRepository
  private lateinit var inventoryRepository: InventoryRepository
  private lateinit var characterTemplateCatalog: CharacterTemplateCatalog
  private lateinit var playerStateWorkQueue: PlayerStateWorkQueue
  private lateinit var combatStatService: CombatStatService
  private lateinit var combatStateRepository: CombatStateRepository
  private lateinit var teamSlot: CapturingSlot<Team>
  private lateinit var service: TeamService

  init {
    beforeTest {
      playerRepository = mockk()
      teamRepository = mockk()
      inventoryRepository = mockk()
      characterTemplateCatalog = mockk()
      playerStateWorkQueue = mockk()
      combatStatService = mockk()
      combatStateRepository = mockk()
      teamSlot = slot()
      service =
        TeamService(
          playerRepository,
          teamRepository,
          inventoryRepository,
          characterTemplateCatalog,
          playerStateWorkQueue,
          combatStatService,
          combatStateRepository,
        )
      every { combatStateRepository.findById(any()) } returns null
    }

    "save loadout applies slot and equipment changes atomically" {
      val playerId = UUID.randomUUID()
      val teamId = UUID.randomUUID()
      val weaponOldId = UUID.randomUUID()
      val weaponNewId = UUID.randomUUID()
      val player = Player(playerId, "Alice", ownedCharacterKeys = setOf("warrior"))
      val team =
        Team(
          teamId,
          player.id,
          slots =
            listOf(
              TeamMemberSlot(1, "warrior", weaponItemId = weaponOldId),
              TeamMemberSlot(2),
              TeamMemberSlot(3),
            ),
        )
      val oldWeapon =
        expectedInventoryItem(
          id = weaponOldId,
          playerId = playerId,
          item = swordItem(),
          equippedTeamId = teamId,
          equippedPosition = 1,
        )
      val newWeapon =
        expectedInventoryItem(
          id = weaponNewId,
          playerId = playerId,
          item = swordItem(),
        )
      val savedItems = mutableListOf<InventoryItem>()
      val savedTeams = mutableListOf<Team>()
      val request =
        listOf(
          TeamSlotLoadoutRequest(
            position = 1,
            characterKey = "warrior",
            weaponItemId = weaponNewId,
            armorItemId = null,
            accessoryItemId = null,
          ),
          TeamSlotLoadoutRequest(
            position = 2,
            characterKey = null,
            weaponItemId = null,
            armorItemId = null,
            accessoryItemId = null,
          ),
          TeamSlotLoadoutRequest(
            position = 3,
            characterKey = null,
            weaponItemId = null,
            armorItemId = null,
            accessoryItemId = null,
          ),
        )

      every { teamRepository.require(teamId) } returns team
      every { playerRepository.require(playerId) } returns player
      every { characterTemplateCatalog.require("warrior") } returns warriorTemplate("warrior")
      every { inventoryRepository.findByPlayerId(playerId) } returns listOf(oldWeapon, newWeapon)
      every { teamRepository.save(any()) } answers { firstArg<Team>().also { savedTeams += it } }
      every { inventoryRepository.save(any()) } answers { firstArg<InventoryItem>().also { savedItems += it } }
      every { combatStatService.invalidatePlayer(playerId) } just runs
      every { playerStateWorkQueue.markDirty(playerId) } just runs

      val savedTeam = service.saveLoadout(teamId, request)

      savedTeam.slots.first().weaponItemId shouldBe weaponNewId
      savedTeams
        .single()
        .slots
        .first()
        .weaponItemId shouldBe weaponNewId
      savedItems.find { it.id == weaponOldId }!!.equippedTeamId shouldBe null
      savedItems.find { it.id == weaponOldId }!!.equippedPosition shouldBe null
      savedItems.find { it.id == weaponNewId }!!.equippedTeamId shouldBe teamId
      savedItems.find { it.id == weaponNewId }!!.equippedPosition shouldBe 1
      verify(exactly = 1) { combatStatService.invalidatePlayer(playerId) }
      verify(exactly = 1) { playerStateWorkQueue.markDirty(playerId) }
    }

    "create team for player" {
      val player = Player(UUID.randomUUID(), "Alice")

      every { playerRepository.require(player.id) } returns player
      every { teamRepository.save(capture(teamSlot)) } answers { teamSlot.captured }
      every { inventoryRepository.findByPlayerId(any()) } returns emptyList()
      every { playerStateWorkQueue.markDirty(player.id) } just runs

      val team = service.create(player.id)

      team shouldBe expectedTeam(team.id, player.id)
      verify(exactly = 1) { teamRepository.save(any()) }
      verify(exactly = 1) { playerStateWorkQueue.markDirty(player.id) }
    }

    "activate team requires at least one character" {
      val player = Player(UUID.randomUUID(), "Alice")
      val team = Team(UUID.randomUUID(), player.id)

      every { teamRepository.require(team.id) } returns team
      every { playerRepository.require(player.id) } returns player

      shouldThrow<ValidationException> {
        service.activate(team.id)
      }
    }

    "assign character to team rejects duplicates across slots" {
      val player = Player(UUID.randomUUID(), "Alice", ownedCharacterKeys = setOf("warrior"))
      val team =
        Team(
          UUID.randomUUID(),
          player.id,
          slots =
            listOf(
              TeamMemberSlot(1, "warrior"),
              TeamMemberSlot(2, "character-1"),
              TeamMemberSlot(3),
            ),
        )
      val characterKey = "warrior"

      every { playerRepository.require(player.id) } returns player
      every { teamRepository.require(team.id) } returns team
      every { characterTemplateCatalog.require(characterKey) } returns warriorTemplate(characterKey)

      shouldThrow<ValidationException> {
        service.assignCharacter(team.id, 3, characterKey)
      }
    }

    "assign character to team adds the character key" {
      val player = Player(UUID.randomUUID(), "Alice", ownedCharacterKeys = setOf("warrior"))
      val team = Team(UUID.randomUUID(), player.id)
      val characterKey = "warrior"
      val savedTeams = mutableListOf<Team>()

      every { playerRepository.require(player.id) } returns player
      every { teamRepository.require(team.id) } returns team
      every { characterTemplateCatalog.require(characterKey) } returns warriorTemplate(characterKey)
      every { teamRepository.save(any()) } answers {
        firstArg<Team>().also { savedTeams += it }
      }
      every { combatStatService.invalidatePlayer(player.id) } just runs
      every { playerStateWorkQueue.markDirty(player.id) } just runs

      val updatedTeam = service.assignCharacter(team.id, 1, characterKey)
      val expectedTeam =
        expectedTeam(
          team.id,
          player.id,
          slots = listOf(TeamMemberSlot(1, "warrior"), TeamMemberSlot(2), TeamMemberSlot(3)),
        )

      updatedTeam shouldBe expectedTeam
      savedTeams.last() shouldBe expectedTeam
      verify(exactly = 1) { combatStatService.invalidatePlayer(player.id) }
      verify(exactly = 1) { playerStateWorkQueue.markDirty(player.id) }
    }

    "assign character to team is blocked while combat is down" {
      val player = Player(UUID.randomUUID(), "Alice", ownedCharacterKeys = setOf("warrior"))
      val team = Team(UUID.randomUUID(), player.id)

      every { teamRepository.require(team.id) } returns team
      every { playerRepository.require(player.id) } returns player
      every {
        combatStateRepository.findById(player.id)
      } returns
        expectedSingleMemberCombatState(
          player.id,
          team.id,
          attack = 10f,
          hit = 10f,
          currentHp = 0f,
          maxHp = 100f,
          status = CombatStatus.DOWN,
          enemyHp = 50f,
          enemyMaxHp = 50f,
        )

      val thrown =
        shouldThrow<ValidationException> {
          service.assignCharacter(team.id, 1, "warrior")
        }

      thrown.shouldMatchExpected(
        expectedValidationException("Team changes are unavailable while the player's combat is downed."),
      )
    }

    "activate team sets player's active team" {
      val player = Player(UUID.randomUUID(), "Alice", ownedCharacterKeys = setOf("warrior"))
      val team =
        Team(
          UUID.randomUUID(),
          player.id,
          slots = listOf(TeamMemberSlot(1, "warrior"), TeamMemberSlot(2), TeamMemberSlot(3)),
        )
      val updatedPlayers = mutableListOf<Player>()

      every { teamRepository.require(team.id) } returns team
      every { playerRepository.require(player.id) } returns player
      every { playerRepository.save(any()) } answers { firstArg<Player>().also { updatedPlayers += it } }
      every { combatStatService.invalidatePlayer(player.id) } just runs
      every { playerStateWorkQueue.markDirty(player.id) } just runs

      val activatedTeam = service.activate(team.id)
      val expectedPlayer = expectedPlayer(player.id, "Alice", ownedCharacterKeys = setOf("warrior"), activeTeamId = team.id)

      activatedTeam shouldBe team
      updatedPlayers.last() shouldBe expectedPlayer
      verify(exactly = 1) { combatStatService.invalidatePlayer(player.id) }
      verify(exactly = 1) { playerStateWorkQueue.markDirty(player.id) }
    }

    "activate team is blocked while combat is down" {
      val player = Player(UUID.randomUUID(), "Alice", ownedCharacterKeys = setOf("warrior"))
      val team =
        Team(
          UUID.randomUUID(),
          player.id,
          slots = listOf(TeamMemberSlot(1, "warrior"), TeamMemberSlot(2), TeamMemberSlot(3)),
        )

      every { teamRepository.require(team.id) } returns team
      every { playerRepository.require(player.id) } returns player
      every {
        combatStateRepository.findById(player.id)
      } returns
        expectedSingleMemberCombatState(
          player.id,
          team.id,
          attack = 10f,
          hit = 10f,
          currentHp = 0f,
          maxHp = 100f,
          status = CombatStatus.DOWN,
          enemyHp = 50f,
          enemyMaxHp = 50f,
        )

      val thrown =
        shouldThrow<ValidationException> {
          service.activate(team.id)
        }

      thrown.shouldMatchExpected(
        expectedValidationException("Team changes are unavailable while the player's combat is downed."),
      )
    }
  }
}
