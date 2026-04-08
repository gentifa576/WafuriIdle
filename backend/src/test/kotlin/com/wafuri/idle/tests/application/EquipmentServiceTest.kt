package com.wafuri.idle.tests.application

import com.wafuri.idle.application.exception.ValidationException
import com.wafuri.idle.application.port.out.CombatStateRepository
import com.wafuri.idle.application.port.out.InventoryRepository
import com.wafuri.idle.application.port.out.PlayerStateWorkQueue
import com.wafuri.idle.application.port.out.Repository
import com.wafuri.idle.application.port.out.TeamRepository
import com.wafuri.idle.application.service.combat.CombatStatService
import com.wafuri.idle.application.service.inventory.EquipmentService
import com.wafuri.idle.domain.model.CombatStatus
import com.wafuri.idle.domain.model.EquipmentSlot
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
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import java.util.UUID

class EquipmentServiceTest : StringSpec() {
  private lateinit var playerRepository: Repository<Player, UUID>
  private lateinit var inventoryRepository: InventoryRepository
  private lateinit var teamRepository: TeamRepository
  private lateinit var playerStateWorkQueue: PlayerStateWorkQueue
  private lateinit var combatStatService: CombatStatService
  private lateinit var combatStateRepository: CombatStateRepository
  private lateinit var service: EquipmentService

  init {
    beforeTest {
      playerRepository = mockk()
      inventoryRepository = mockk()
      teamRepository = mockk()
      playerStateWorkQueue = mockk()
      combatStatService = mockk()
      combatStateRepository = mockk()
      service =
        EquipmentService(
          playerRepository,
          inventoryRepository,
          teamRepository,
          playerStateWorkQueue,
          combatStatService,
          combatStateRepository,
        )
      every { combatStateRepository.findById(any()) } returns null
    }

    "equip validates ownership and slot" {
      val playerId = UUID.randomUUID()
      val teamId = UUID.randomUUID()
      val player = expectedPlayer(playerId, "Alice", ownedCharacterKeys = setOf("warrior"))
      val team =
        expectedTeam(
          id = teamId,
          playerId = playerId,
          slots = listOf(TeamMemberSlot(1, "warrior"), TeamMemberSlot(2), TeamMemberSlot(3)),
        )
      val foreignItem =
        expectedInventoryItem(
          id = UUID.randomUUID(),
          playerId = UUID.randomUUID(),
          item = swordItem(),
        )

      every { inventoryRepository.require(foreignItem.id) } returns foreignItem
      every { teamRepository.require(teamId) } returns team
      every { playerRepository.require(foreignItem.playerId) } returns player
      every { inventoryRepository.findByTeamPositionAndSlot(teamId, 1, EquipmentSlot.WEAPON) } returns null

      shouldThrow<ValidationException> {
        service.equip(teamId, 1, foreignItem.id, EquipmentSlot.WEAPON)
      }
    }

    "unequip returns item to inventory" {
      val playerId = UUID.randomUUID()
      val inventoryItemId = UUID.randomUUID()
      val teamId = UUID.randomUUID()
      val player = expectedPlayer(playerId, "Alice", ownedCharacterKeys = setOf("warrior"))
      val team =
        expectedTeam(
          id = teamId,
          playerId = playerId,
          slots =
            listOf(
              TeamMemberSlot(1, "warrior", weaponItemId = inventoryItemId),
              TeamMemberSlot(2),
              TeamMemberSlot(3),
            ),
        )
      val inventoryItem =
        expectedInventoryItem(
          id = inventoryItemId,
          playerId = playerId,
          item = swordItem(),
          equippedTeamId = teamId,
          equippedPosition = 1,
        )
      val savedItems = mutableListOf<InventoryItem>()

      every { inventoryRepository.findByTeamPositionAndSlot(teamId, 1, EquipmentSlot.WEAPON) } returns inventoryItem
      every { teamRepository.require(teamId) } returns team
      every { playerRepository.require(playerId) } returns player
      every { inventoryRepository.save(any()) } answers {
        firstArg<InventoryItem>().also { savedItems += it }
      }
      every { teamRepository.save(any()) } answers { firstArg<Team>() }
      every { combatStatService.invalidatePlayer(playerId) } just runs
      every { playerStateWorkQueue.markDirty(playerId) } just runs

      service.unequip(teamId, 1, EquipmentSlot.WEAPON)

      savedItems.last().equippedTeamId.shouldBeNull()
      savedItems.last().equippedPosition.shouldBeNull()
      verify(exactly = 1) { combatStatService.invalidatePlayer(playerId) }
      verify(exactly = 1) { playerStateWorkQueue.markDirty(playerId) }
    }

    "equip swaps the existing item in the same slot without a separate unequip" {
      val playerId = UUID.randomUUID()
      val equippedItemId = UUID.randomUUID()
      val replacementItemId = UUID.randomUUID()
      val teamId = UUID.randomUUID()
      val player = expectedPlayer(playerId, "Alice", ownedCharacterKeys = setOf("warrior"))
      val team =
        expectedTeam(
          id = teamId,
          playerId = playerId,
          slots =
            listOf(
              TeamMemberSlot(1, "warrior", weaponItemId = equippedItemId),
              TeamMemberSlot(2),
              TeamMemberSlot(3),
            ),
        )
      val equippedItem =
        expectedInventoryItem(
          id = equippedItemId,
          playerId = playerId,
          item = swordItem(),
          equippedTeamId = teamId,
          equippedPosition = 1,
        )
      val replacementItem =
        expectedInventoryItem(
          id = replacementItemId,
          playerId = playerId,
          item = swordItem(),
        )
      val savedItems = mutableListOf<InventoryItem>()
      val savedTeams = mutableListOf<Team>()

      every { inventoryRepository.require(replacementItemId) } returns replacementItem
      every { teamRepository.require(teamId) } returns team
      every { playerRepository.require(playerId) } returns player
      every { inventoryRepository.findByTeamPositionAndSlot(teamId, 1, EquipmentSlot.WEAPON) } returns equippedItem
      every { inventoryRepository.save(any()) } answers {
        firstArg<InventoryItem>().also { savedItems += it }
      }
      every { teamRepository.save(any()) } answers { firstArg<Team>().also { savedTeams += it } }
      every { combatStatService.invalidatePlayer(playerId) } just runs
      every { playerStateWorkQueue.markDirty(playerId) } just runs

      service.equip(teamId, 1, replacementItemId, EquipmentSlot.WEAPON)

      savedItems.size.shouldBe(2)
      savedItems.first().id.shouldBe(equippedItemId)
      savedItems.first().equippedTeamId.shouldBeNull()
      savedItems.first().equippedPosition.shouldBeNull()
      savedItems.last().id.shouldBe(replacementItemId)
      savedItems.last().equippedTeamId.shouldBe(teamId)
      savedItems.last().equippedPosition.shouldBe(1)
      savedTeams
        .single()
        .slots
        .first()
        .weaponItemId
        .shouldBe(replacementItemId)
      verify(exactly = 1) { combatStatService.invalidatePlayer(playerId) }
      verify(exactly = 1) { playerStateWorkQueue.markDirty(playerId) }
    }

    "equip is blocked while combat is down" {
      val playerId = UUID.randomUUID()
      val inventoryItemId = UUID.randomUUID()
      val teamId = UUID.randomUUID()
      val player = expectedPlayer(playerId, "Alice", ownedCharacterKeys = setOf("warrior"))
      val team =
        expectedTeam(
          id = teamId,
          playerId = playerId,
          slots = listOf(TeamMemberSlot(1, "warrior"), TeamMemberSlot(2), TeamMemberSlot(3)),
        )
      val inventoryItem =
        expectedInventoryItem(
          id = inventoryItemId,
          playerId = playerId,
          item = swordItem(),
        )

      every { inventoryRepository.require(inventoryItemId) } returns inventoryItem
      every { teamRepository.require(teamId) } returns team
      every { playerRepository.require(playerId) } returns player
      every {
        combatStateRepository.findById(playerId)
      } returns
        expectedSingleMemberCombatState(
          playerId,
          teamId,
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
          service.equip(teamId, 1, inventoryItemId, EquipmentSlot.WEAPON)
        }

      thrown.shouldMatchExpected(
        expectedValidationException("Team changes are unavailable while the player's combat is downed."),
      )
    }

    "unequip is blocked while combat is down" {
      val playerId = UUID.randomUUID()
      val inventoryItemId = UUID.randomUUID()
      val teamId = UUID.randomUUID()
      val player = expectedPlayer(playerId, "Alice", ownedCharacterKeys = setOf("warrior"))
      val team =
        expectedTeam(
          id = teamId,
          playerId = playerId,
          slots =
            listOf(
              TeamMemberSlot(1, "warrior", weaponItemId = inventoryItemId),
              TeamMemberSlot(2),
              TeamMemberSlot(3),
            ),
        )
      val inventoryItem =
        expectedInventoryItem(
          id = inventoryItemId,
          playerId = playerId,
          item = swordItem(),
          equippedTeamId = teamId,
          equippedPosition = 1,
        )

      every { inventoryRepository.findByTeamPositionAndSlot(teamId, 1, EquipmentSlot.WEAPON) } returns inventoryItem
      every { teamRepository.require(teamId) } returns team
      every { playerRepository.require(playerId) } returns player
      every {
        combatStateRepository.findById(playerId)
      } returns
        expectedSingleMemberCombatState(
          playerId,
          teamId,
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
          service.unequip(teamId, 1, EquipmentSlot.WEAPON)
        }

      thrown.shouldMatchExpected(
        expectedValidationException("Team changes are unavailable while the player's combat is downed."),
      )
    }
  }
}
