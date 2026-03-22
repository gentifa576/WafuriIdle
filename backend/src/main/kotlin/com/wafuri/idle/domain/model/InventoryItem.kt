package com.wafuri.idle.domain.model

import java.util.UUID

data class InventoryItem(
  val id: UUID,
  val playerId: UUID,
  val item: Item,
  val subStats: List<Stat> = emptyList(),
  val rarity: Rarity = Rarity.COMMON,
  val upgrade: Int = 0,
  val equippedTeamId: UUID? = null,
  val equippedPosition: Int? = null,
) {
  init {
    require(subStats.map { it.type }.distinct().size == subStats.size) {
      "Inventory item sub stats must be unique."
    }
    require(subStats.all { it.type in item.subStatPool }) {
      "Inventory item sub stats must come from the item sub stat pool."
    }
    require(upgrade >= 0) { "Inventory item upgrade must not be negative." }
    require((equippedTeamId == null) == (equippedPosition == null)) {
      "Equipped team and position must either both be set or both be null."
    }
    require(equippedPosition == null || equippedPosition in 1..Team.MAX_SIZE) {
      "Equipped position must be between 1 and ${Team.MAX_SIZE}."
    }
  }

  fun equip(
    playerId: UUID,
    teamId: UUID,
    position: Int,
    slot: EquipmentSlot,
  ): InventoryItem {
    if (this.playerId != playerId) {
      throw DomainRuleViolationException("Items must belong to the player's inventory.")
    }
    if (position !in 1..Team.MAX_SIZE) {
      throw DomainRuleViolationException("Team slot position must be between 1 and ${Team.MAX_SIZE}.")
    }
    if (equippedTeamId != null) {
      throw DomainRuleViolationException("Items cannot be equipped twice.")
    }
    if (item.type != slot.allowedType) {
      throw DomainRuleViolationException("Item type must match equipment slot.")
    }
    return copy(equippedTeamId = teamId, equippedPosition = position)
  }

  fun unequip(
    teamId: UUID,
    position: Int,
    slot: EquipmentSlot,
  ): InventoryItem {
    if (equippedTeamId != teamId || equippedPosition != position || item.type != slot.allowedType) {
      throw DomainRuleViolationException("Item is not equipped in the requested slot.")
    }
    return copy(equippedTeamId = null, equippedPosition = null)
  }
}
