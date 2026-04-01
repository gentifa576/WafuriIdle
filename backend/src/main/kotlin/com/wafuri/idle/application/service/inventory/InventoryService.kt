package com.wafuri.idle.application.service.inventory

import com.wafuri.idle.application.exception.ResourceNotFoundException
import com.wafuri.idle.application.port.out.InventoryRepository
import com.wafuri.idle.application.port.out.PlayerStateWorkQueue
import com.wafuri.idle.application.port.out.Repository
import com.wafuri.idle.application.service.item.ItemTemplateCatalog
import com.wafuri.idle.domain.model.InventoryItem
import com.wafuri.idle.domain.model.Player
import com.wafuri.idle.domain.model.Rarity
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import java.util.UUID

@ApplicationScoped
class InventoryService(
  private val playerRepository: Repository<Player, UUID>,
  private val itemTemplateCatalog: ItemTemplateCatalog,
  private val inventoryRepository: InventoryRepository,
  private val playerStateWorkQueue: PlayerStateWorkQueue,
) {
  @Transactional
  fun addGeneratedItem(
    playerId: UUID,
    name: String,
    rarity: Rarity,
    itemLevel: Int = 1,
  ): InventoryItem {
    playerRepository.findById(playerId)
      ?: throw ResourceNotFoundException("Player $playerId was not found.")

    val item = itemTemplateCatalog.require(name)
    val inventoryItem =
      inventoryRepository.save(
        InventoryItem(
          UUID.randomUUID(),
          playerId,
          item,
          itemLevel,
          rarity = rarity,
        ),
      )
    playerStateWorkQueue.markDirty(playerId)
    return inventoryItem
  }

  fun getInventory(playerId: UUID): List<InventoryItem> {
    playerRepository.findById(playerId)
      ?: throw ResourceNotFoundException("Player $playerId was not found.")
    return inventoryRepository.findByPlayerId(playerId)
  }
}
