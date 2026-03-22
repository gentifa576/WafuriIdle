package com.wafuri.idle.domain.model

import java.util.UUID

data class Team(
  val id: UUID,
  val playerId: UUID,
  val slots: List<TeamMemberSlot> = defaultSlots(),
) {
  init {
    require(slots.size == MAX_SIZE) { "Team must have exactly $MAX_SIZE slots." }
    require(slots.map { it.position }.sorted() == (1..MAX_SIZE).toList()) {
      "Team slot positions must be exactly 1 through $MAX_SIZE."
    }
    require(characterKeys.distinct().size == characterKeys.size) {
      "Duplicate characters are not allowed in a team."
    }
  }

  val characterKeys: List<String>
    get() = slots.mapNotNull { it.characterKey }

  fun assignCharacter(
    position: Int,
    characterKey: String,
  ): Team {
    requireValidPosition(position)
    if (characterKey.isBlank()) {
      throw DomainRuleViolationException("Character key must not be blank.")
    }
    if (slots.any { it.position != position && it.characterKey == characterKey }) {
      throw DomainRuleViolationException("Character is already on the team.")
    }
    return copy(
      slots =
        slots.map { slot ->
          if (slot.position == position) {
            slot.copy(characterKey = characterKey)
          } else {
            slot
          }
        },
    )
  }

  fun equipItem(
    position: Int,
    slot: EquipmentSlot,
    inventoryItemId: UUID,
  ): Team {
    requireValidPosition(position)
    return copy(
      slots =
        slots.map { teamSlot ->
          if (teamSlot.position == position) {
            teamSlot.equip(slot, inventoryItemId)
          } else {
            teamSlot
          }
        },
    )
  }

  fun unequipItem(
    position: Int,
    slot: EquipmentSlot,
  ): Team {
    requireValidPosition(position)
    return copy(
      slots =
        slots.map { teamSlot ->
          if (teamSlot.position == position) {
            teamSlot.unequip(slot)
          } else {
            teamSlot
          }
        },
    )
  }

  companion object {
    const val MAX_SIZE = 3

    fun defaultSlots(): List<TeamMemberSlot> = (1..MAX_SIZE).map(::TeamMemberSlot)

    private fun requireValidPosition(position: Int) {
      if (position !in 1..MAX_SIZE) {
        throw DomainRuleViolationException("Team slot position must be between 1 and $MAX_SIZE.")
      }
    }
  }
}

data class TeamMemberSlot(
  val position: Int,
  val characterKey: String? = null,
  val weaponItemId: UUID? = null,
  val armorItemId: UUID? = null,
  val accessoryItemId: UUID? = null,
) {
  init {
    require(position in 1..Team.MAX_SIZE) { "Team slot position must be between 1 and ${Team.MAX_SIZE}." }
    require(characterKey == null || characterKey.isNotBlank()) { "Character key must not be blank." }
  }

  fun equip(
    slot: EquipmentSlot,
    inventoryItemId: UUID,
  ): TeamMemberSlot {
    if (characterKey == null) {
      throw DomainRuleViolationException("Items cannot be equipped to an empty team slot.")
    }
    return when (slot) {
      EquipmentSlot.WEAPON -> copy(weaponItemId = inventoryItemId)
      EquipmentSlot.ARMOR -> copy(armorItemId = inventoryItemId)
      EquipmentSlot.ACCESSORY -> copy(accessoryItemId = inventoryItemId)
    }
  }

  fun unequip(slot: EquipmentSlot): TeamMemberSlot =
    when (slot) {
      EquipmentSlot.WEAPON -> copy(weaponItemId = null)
      EquipmentSlot.ARMOR -> copy(armorItemId = null)
      EquipmentSlot.ACCESSORY -> copy(accessoryItemId = null)
    }

  fun itemIdFor(slot: EquipmentSlot): UUID? =
    when (slot) {
      EquipmentSlot.WEAPON -> weaponItemId
      EquipmentSlot.ARMOR -> armorItemId
      EquipmentSlot.ACCESSORY -> accessoryItemId
    }
}
