package com.wafuri.idle.application.service.player

import com.wafuri.idle.application.model.InventoryItemSnapshot
import com.wafuri.idle.application.model.OwnedCharacterSnapshot
import com.wafuri.idle.application.model.PlayerStateSnapshot
import com.wafuri.idle.application.model.toSnapshot
import com.wafuri.idle.application.port.out.CombatStateRepository
import com.wafuri.idle.application.port.out.InventoryRepository
import com.wafuri.idle.application.port.out.PlayerZoneProgressRepository
import com.wafuri.idle.application.port.out.Repository
import com.wafuri.idle.application.service.character.CharacterTemplateCatalog
import com.wafuri.idle.application.service.scaling.ScalingRule
import com.wafuri.idle.domain.model.CombatStatus
import com.wafuri.idle.domain.model.Player
import jakarta.enterprise.context.ApplicationScoped
import java.time.Instant
import java.util.UUID

@ApplicationScoped
class PlayerStateSnapshotService(
  private val playerRepository: Repository<Player, UUID>,
  private val inventoryRepository: InventoryRepository,
  private val playerZoneProgressRepository: PlayerZoneProgressRepository,
  private val combatStateRepository: CombatStateRepository,
  private val characterTemplateCatalog: CharacterTemplateCatalog,
  private val scalingRule: ScalingRule,
) {
  fun snapshotFor(playerId: UUID): PlayerStateSnapshot {
    val player = playerRepository.require(playerId)
    val inventory = inventoryRepository.findByPlayerId(playerId)
    val zoneProgress = playerZoneProgressRepository.findByPlayerId(playerId)

    return PlayerStateSnapshot(
      playerId = player.id,
      playerName = player.name,
      playerExperience = player.experience,
      playerLevel = player.level,
      playerGold = player.gold,
      playerEssence = player.essence,
      ownedCharacters =
        player.ownedCharacterKeys
          .sorted()
          .map { key ->
            val template = characterTemplateCatalog.require(key)
            OwnedCharacterSnapshot(key = template.key, name = template.name, level = player.level)
          },
      zoneProgress = zoneProgress.map { it.toSnapshot() },
      inventory =
        inventory.map {
          InventoryItemSnapshot(
            id = it.id,
            itemLevel = it.itemLevel,
            itemName = it.item.name,
            itemDisplayName = it.item.displayName,
            itemType = it.item.type,
            itemBaseStat = scalingRule.scaledBaseStat(it),
            itemSubStatPool = it.item.subStatPool,
            subStats = scalingRule.scaledSubStats(it),
            rarity = it.rarity,
            upgrade = it.upgrade,
            equippedTeamId = it.equippedTeamId,
            equippedPosition = it.equippedPosition,
          )
        },
      serverTime = Instant.now(),
    )
  }

  fun combatSnapshotFor(playerId: UUID) =
    combatStateRepository
      .findById(playerId)
      ?.takeUnless { it.status == CombatStatus.IDLE }
      ?.toSnapshot()
}
