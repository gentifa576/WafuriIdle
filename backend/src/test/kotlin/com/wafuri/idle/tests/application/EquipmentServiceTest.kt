package com.wafuri.idle.tests.application

import com.wafuri.idle.application.exception.ValidationException
import com.wafuri.idle.application.port.out.InventoryRepository
import com.wafuri.idle.application.port.out.PlayerStateWorkQueue
import com.wafuri.idle.application.port.out.Repository
import com.wafuri.idle.application.port.out.TeamRepository
import com.wafuri.idle.application.service.combat.CombatStatService
import com.wafuri.idle.application.service.inventory.EquipmentService
import com.wafuri.idle.domain.model.EquipmentSlot
import com.wafuri.idle.domain.model.InventoryItem
import com.wafuri.idle.domain.model.Player
import com.wafuri.idle.domain.model.Team
import com.wafuri.idle.domain.model.TeamMemberSlot
import com.wafuri.idle.tests.support.expectedInventoryItem
import com.wafuri.idle.tests.support.expectedPlayer
import com.wafuri.idle.tests.support.expectedTeam
import com.wafuri.idle.tests.support.swordItem
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
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
  private lateinit var service: EquipmentService

  init {
    beforeTest {
      playerRepository = mockk()
      inventoryRepository = mockk()
      teamRepository = mockk()
      playerStateWorkQueue = mockk()
      combatStatService = mockk()
      service = EquipmentService(playerRepository, inventoryRepository, teamRepository, playerStateWorkQueue, combatStatService)
    }

    "equip validates ownership and slot" {
      val playerId = UUID.randomUUID()
      val teamId = UUID.randomUUID()
      val player = expectedPlayer(id = playerId, name = "Alice", ownedCharacterKeys = setOf("warrior"))
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

      every { inventoryRepository.findById(foreignItem.id) } returns foreignItem
      every { teamRepository.findById(teamId) } returns team
      every { playerRepository.findById(foreignItem.playerId) } returns player
      every { inventoryRepository.findByTeamPositionAndSlot(teamId, 1, EquipmentSlot.WEAPON) } returns null

      shouldThrow<ValidationException> {
        service.equip(playerId, teamId, 1, foreignItem.id, EquipmentSlot.WEAPON)
      }
    }

    "unequip returns item to inventory" {
      val playerId = UUID.randomUUID()
      val inventoryItemId = UUID.randomUUID()
      val teamId = UUID.randomUUID()
      val player = expectedPlayer(id = playerId, name = "Alice", ownedCharacterKeys = setOf("warrior"))
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
      every { teamRepository.findById(teamId) } returns team
      every { playerRepository.findById(playerId) } returns player
      every { inventoryRepository.save(any()) } answers {
        firstArg<InventoryItem>().also { savedItems += it }
      }
      every { teamRepository.save(any()) } answers { firstArg<Team>() }
      every { combatStatService.invalidatePlayer(playerId) } just runs
      every { playerStateWorkQueue.markDirty(playerId) } just runs

      service.unequip(playerId, teamId, 1, EquipmentSlot.WEAPON)

      savedItems.last().equippedTeamId.shouldBeNull()
      savedItems.last().equippedPosition.shouldBeNull()
      verify(exactly = 1) { combatStatService.invalidatePlayer(playerId) }
      verify(exactly = 1) { playerStateWorkQueue.markDirty(playerId) }
    }
  }
}
