package com.wafuri.idle.domain.model

import java.util.UUID

data class InventoryItem(
  val id: UUID,
  val playerId: UUID,
  val item: Item,
  val subStats: List<Stat> = emptyList(),
  val rarity: Rarity = Rarity.COMMON,
  val upgrade: Int = 0,
  val equippedCharacterKey: String? = null,
) {
  init {
    require(subStats.map { it.type }.distinct().size == subStats.size) {
      "Inventory item sub stats must be unique."
    }
    require(subStats.all { it.type in item.subStatPool }) {
      "Inventory item sub stats must come from the item sub stat pool."
    }
    require(upgrade >= 0) { "Inventory item upgrade must not be negative." }
  }

  fun equip(
    playerId: UUID,
    characterKey: String,
    slot: EquipmentSlot,
  ): InventoryItem {
    if (this.playerId != playerId) {
      throw DomainRuleViolationException("Items must belong to the player's inventory.")
    }
    if (characterKey.isBlank()) {
      throw DomainRuleViolationException("Character key must not be blank.")
    }
    if (equippedCharacterKey != null) {
      throw DomainRuleViolationException("Items cannot be equipped twice.")
    }
    if (item.type != slot.allowedType) {
      throw DomainRuleViolationException("Item type must match equipment slot.")
    }
    return copy(equippedCharacterKey = characterKey)
  }

  fun unequip(
    characterKey: String,
    slot: EquipmentSlot,
  ): InventoryItem {
    if (equippedCharacterKey != characterKey || item.type != slot.allowedType) {
      throw DomainRuleViolationException("Item is not equipped in the requested slot.")
    }
    return copy(equippedCharacterKey = null)
  }
}
