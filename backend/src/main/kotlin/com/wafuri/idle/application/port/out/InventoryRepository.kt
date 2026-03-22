package com.wafuri.idle.application.port.out

import com.wafuri.idle.domain.model.EquipmentSlot
import com.wafuri.idle.domain.model.InventoryItem
import java.util.UUID

interface InventoryRepository : Repository<InventoryItem, UUID> {
  fun findByPlayerId(playerId: UUID): List<InventoryItem>

  fun findByCharacterAndSlot(
    characterKey: String,
    slot: EquipmentSlot,
  ): InventoryItem?
}
