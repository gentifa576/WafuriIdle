package com.wafuri.idle.application.service.inventory

import com.wafuri.idle.application.exception.ResourceNotFoundException
import com.wafuri.idle.application.exception.ValidationException
import com.wafuri.idle.application.port.out.CombatStateRepository
import com.wafuri.idle.application.port.out.InventoryRepository
import com.wafuri.idle.application.port.out.PlayerStateWorkQueue
import com.wafuri.idle.application.port.out.Repository
import com.wafuri.idle.application.port.out.TeamRepository
import com.wafuri.idle.application.service.combat.CombatStatService
import com.wafuri.idle.domain.model.CombatStatus
import com.wafuri.idle.domain.model.DomainRuleViolationException
import com.wafuri.idle.domain.model.EquipmentSlot
import com.wafuri.idle.domain.model.Player
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import java.util.UUID

@ApplicationScoped
class EquipmentService(
  private val playerRepository: Repository<Player, UUID>,
  private val inventoryRepository: InventoryRepository,
  private val teamRepository: TeamRepository,
  private val playerStateWorkQueue: PlayerStateWorkQueue,
  private val combatStatService: CombatStatService,
  private val combatStateRepository: CombatStateRepository,
) {
  @Transactional
  fun equip(
    teamId: UUID,
    position: Int,
    inventoryItemId: UUID,
    slot: EquipmentSlot,
  ) {
    val inventoryItem = inventoryRepository.require(inventoryItemId)
    val team = teamRepository.require(teamId)
    val player = playerRepository.require(inventoryItem.playerId)
    requirePlayerNotDowned(player.id)
    val teamSlot =
      team.slots.firstOrNull { it.position == position }
        ?: throw ValidationException("Team slot position must be between 1 and ${com.wafuri.idle.domain.model.Team.MAX_SIZE}.")
    if (teamSlot.characterKey == null) {
      throw ValidationException("Items cannot be equipped to an empty team slot.")
    }
    val equippedItem = inventoryRepository.findByTeamPositionAndSlot(teamId, position, slot)
    if (equippedItem != null && equippedItem.id != inventoryItem.id) {
      throw ValidationException("Only one item may be equipped per team slot.")
    }

    val updatedItem =
      try {
        inventoryItem.equip(player.id, teamId, position, slot)
      } catch (exception: DomainRuleViolationException) {
        throw ValidationException(exception.message ?: "Equipment validation failed.", exception)
      }
    val updatedTeam =
      try {
        team.equipItem(position, slot, inventoryItem.id)
      } catch (exception: DomainRuleViolationException) {
        throw ValidationException(exception.message ?: "Team slot equipment validation failed.", exception)
      }

    teamRepository.save(updatedTeam)
    inventoryRepository.save(updatedItem)
    combatStatService.invalidatePlayer(player.id)
    playerStateWorkQueue.markDirty(player.id)
  }

  @Transactional
  fun unequip(
    teamId: UUID,
    position: Int,
    slot: EquipmentSlot,
  ) {
    val inventoryItem =
      inventoryRepository.findByTeamPositionAndSlot(teamId, position, slot)
        ?: throw ResourceNotFoundException("No equipped item found in slot $slot for team $teamId position $position.")
    val team = teamRepository.require(teamId)
    val player = playerRepository.require(inventoryItem.playerId)
    requirePlayerNotDowned(player.id)

    val updatedItem =
      try {
        inventoryItem.unequip(teamId, position, slot)
      } catch (exception: DomainRuleViolationException) {
        throw ValidationException(exception.message ?: "Unequip validation failed.", exception)
      }
    val updatedTeam =
      try {
        team.unequipItem(position, slot)
      } catch (exception: DomainRuleViolationException) {
        throw ValidationException(exception.message ?: "Team slot unequip validation failed.", exception)
      }

    teamRepository.save(updatedTeam)
    inventoryRepository.save(updatedItem)
    combatStatService.invalidatePlayer(player.id)
    playerStateWorkQueue.markDirty(player.id)
  }

  private fun requirePlayerNotDowned(playerId: UUID) {
    if (combatStateRepository.findById(playerId)?.status == CombatStatus.DOWN) {
      throw ValidationException("Team changes are unavailable while the player's combat is downed.")
    }
  }
}
