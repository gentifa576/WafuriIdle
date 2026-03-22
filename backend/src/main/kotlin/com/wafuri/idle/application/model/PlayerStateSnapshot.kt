package com.wafuri.idle.application.model

import com.wafuri.idle.domain.model.ItemType
import com.wafuri.idle.domain.model.PlayerZoneProgress
import com.wafuri.idle.domain.model.Rarity
import com.wafuri.idle.domain.model.Stat
import com.wafuri.idle.domain.model.StatType
import java.time.Instant
import java.util.UUID

data class PlayerStateSnapshot(
  val playerId: UUID,
  val playerName: String,
  val playerExperience: Int,
  val playerLevel: Int,
  val ownedCharacters: List<OwnedCharacterSnapshot>,
  val zoneProgress: List<ZoneProgressSnapshot>,
  val inventory: List<InventoryItemSnapshot>,
  val serverTime: Instant,
) {
  fun content(): PlayerStateContent =
    PlayerStateContent(
      playerId = playerId,
      playerName = playerName,
      playerExperience = playerExperience,
      playerLevel = playerLevel,
      ownedCharacters = ownedCharacters,
      zoneProgress = zoneProgress,
      inventory = inventory,
    )
}

data class PlayerStateContent(
  val playerId: UUID,
  val playerName: String,
  val playerExperience: Int,
  val playerLevel: Int,
  val ownedCharacters: List<OwnedCharacterSnapshot>,
  val zoneProgress: List<ZoneProgressSnapshot>,
  val inventory: List<InventoryItemSnapshot>,
)

data class OwnedCharacterSnapshot(
  val key: String,
  val name: String,
  val level: Int,
)

data class ZoneProgressSnapshot(
  val zoneId: String,
  val killCount: Int,
  val level: Int,
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
  val equippedTeamId: UUID?,
  val equippedPosition: Int?,
)

fun PlayerZoneProgress.toSnapshot(): ZoneProgressSnapshot =
  ZoneProgressSnapshot(
    zoneId = zoneId,
    killCount = killCount,
    level = level,
  )
