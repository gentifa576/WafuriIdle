package com.wafuri.idle.application.model

import com.wafuri.idle.domain.model.ItemType
import com.wafuri.idle.domain.model.Rarity
import com.wafuri.idle.domain.model.Stat
import com.wafuri.idle.domain.model.StatType
import java.time.Instant
import java.util.UUID

data class PlayerStateSnapshot(
  val playerId: UUID,
  val playerName: String,
  val ownedCharacters: List<OwnedCharacterSnapshot>,
  val inventory: List<InventoryItemSnapshot>,
  val serverTime: Instant,
) {
  fun content(): PlayerStateContent =
    PlayerStateContent(
      playerId = playerId,
      playerName = playerName,
      ownedCharacters = ownedCharacters,
      inventory = inventory,
    )
}

data class PlayerStateContent(
  val playerId: UUID,
  val playerName: String,
  val ownedCharacters: List<OwnedCharacterSnapshot>,
  val inventory: List<InventoryItemSnapshot>,
)

data class OwnedCharacterSnapshot(
  val key: String,
  val name: String,
)

data class InventoryItemSnapshot(
  val id: UUID,
  val itemName: String,
  val itemDisplayName: String,
  val itemType: ItemType,
  val itemBaseStat: Stat,
  val itemSubStatPool: List<StatType>,
  val subStats: List<Stat>,
  val rarity: Rarity,
  val upgrade: Int,
  val equippedCharacterKey: String?,
)
