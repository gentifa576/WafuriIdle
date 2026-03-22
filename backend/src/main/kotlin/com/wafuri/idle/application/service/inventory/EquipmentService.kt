package com.wafuri.idle.application.service.inventory

import com.wafuri.idle.application.exception.ResourceNotFoundException
import com.wafuri.idle.application.exception.ValidationException
import com.wafuri.idle.application.port.out.InventoryRepository
import com.wafuri.idle.application.port.out.PlayerStateWorkQueue
import com.wafuri.idle.application.port.out.Repository
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
  private val playerStateWorkQueue: PlayerStateWorkQueue,
) {
  @Transactional
  fun equip(
    characterKey: String,
    inventoryItemId: UUID,
    slot: EquipmentSlot,
  ) {
    val inventoryItem =
      inventoryRepository.findById(inventoryItemId)
        ?: throw ResourceNotFoundException("Inventory item $inventoryItemId was not found.")
    val player =
      playerRepository.findById(inventoryItem.playerId)
        ?: throw ResourceNotFoundException("Player ${inventoryItem.playerId} was not found.")
    if (!player.ownedCharacterKeys.contains(characterKey)) {
      throw ValidationException("Character $characterKey is not owned by the player.")
    }
    val equippedItem = inventoryRepository.findByCharacterAndSlot(characterKey, slot)
    if (equippedItem != null && equippedItem.id != inventoryItem.id) {
      throw ValidationException("Only one item may be equipped per slot.")
    }

    val updatedItem =
      try {
        inventoryItem.equip(playerId = player.id, characterKey = characterKey, slot = slot)
      } catch (exception: DomainRuleViolationException) {
        throw ValidationException(exception.message ?: "Equipment validation failed.", exception)
      }

    inventoryRepository.save(updatedItem)
    playerStateWorkQueue.markDirty(player.id)
  }

  @Transactional
  fun unequip(
    characterKey: String,
    slot: EquipmentSlot,
  ) {
    val inventoryItem =
      inventoryRepository.findByCharacterAndSlot(characterKey, slot)
        ?: throw ResourceNotFoundException("No equipped item found in slot $slot for character $characterKey.")
    val player =
      playerRepository.findById(inventoryItem.playerId)
        ?: throw ResourceNotFoundException("Player ${inventoryItem.playerId} was not found.")
    if (!player.ownedCharacterKeys.contains(characterKey)) {
      throw ValidationException("Character $characterKey is not owned by the player.")
    }

    val updatedItem =
      try {
        inventoryItem.unequip(characterKey, slot)
      } catch (exception: DomainRuleViolationException) {
        throw ValidationException(exception.message ?: "Unequip validation failed.", exception)
      }

    inventoryRepository.save(updatedItem)
    playerStateWorkQueue.markDirty(player.id)
  }
}
