package com.wafuri.idle.application.service.inventory

import com.wafuri.idle.application.exception.ResourceNotFoundException
import com.wafuri.idle.application.exception.ValidationException
import com.wafuri.idle.application.port.out.InventoryRepository
import com.wafuri.idle.application.port.out.PlayerStateWorkQueue
import com.wafuri.idle.application.port.out.Repository
import com.wafuri.idle.application.port.out.TeamRepository
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
) {
  @Transactional
  fun equip(
    actorPlayerId: UUID,
    teamId: UUID,
    position: Int,
    inventoryItemId: UUID,
    slot: EquipmentSlot,
  ) {
    val inventoryItem =
      inventoryRepository.findById(inventoryItemId)
        ?: throw ResourceNotFoundException("Inventory item $inventoryItemId was not found.")
    val team =
      teamRepository.findById(teamId)
        ?: throw ResourceNotFoundException("Team $teamId was not found.")
    val player =
      playerRepository.findById(inventoryItem.playerId)
        ?: throw ResourceNotFoundException("Player ${inventoryItem.playerId} was not found.")
    if (team.playerId != actorPlayerId || player.id != actorPlayerId) {
      throw ValidationException("Team does not belong to the authenticated player.")
    }
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
        inventoryItem.equip(playerId = player.id, teamId = teamId, position = position, slot = slot)
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
    playerStateWorkQueue.markDirty(player.id)
  }

  @Transactional
  fun unequip(
    actorPlayerId: UUID,
    teamId: UUID,
    position: Int,
    slot: EquipmentSlot,
  ) {
    val inventoryItem =
      inventoryRepository.findByTeamPositionAndSlot(teamId, position, slot)
        ?: throw ResourceNotFoundException("No equipped item found in slot $slot for team $teamId position $position.")
    val team =
      teamRepository.findById(teamId)
        ?: throw ResourceNotFoundException("Team $teamId was not found.")
    val player =
      playerRepository.findById(inventoryItem.playerId)
        ?: throw ResourceNotFoundException("Player ${inventoryItem.playerId} was not found.")
    if (team.playerId != actorPlayerId || player.id != actorPlayerId) {
      throw ValidationException("Team does not belong to the authenticated player.")
    }

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
    playerStateWorkQueue.markDirty(player.id)
  }
}
