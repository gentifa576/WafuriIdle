package com.wafuri.idle.application.service.player

import com.wafuri.idle.application.exception.ResourceNotFoundException
import com.wafuri.idle.application.model.InventoryItemSnapshot
import com.wafuri.idle.application.model.OwnedCharacterSnapshot
import com.wafuri.idle.application.model.PlayerStateSnapshot
import com.wafuri.idle.application.model.toSnapshot
import com.wafuri.idle.application.port.out.CombatStateRepository
import com.wafuri.idle.application.port.out.InventoryRepository
import com.wafuri.idle.application.port.out.Repository
import com.wafuri.idle.application.service.character.CharacterTemplateCatalog
import com.wafuri.idle.domain.model.Player
import jakarta.enterprise.context.ApplicationScoped
import java.time.Instant
import java.util.UUID

@ApplicationScoped
class PlayerStateSnapshotService(
  private val playerRepository: Repository<Player, UUID>,
  private val inventoryRepository: InventoryRepository,
  private val combatStateRepository: CombatStateRepository,
  private val characterTemplateCatalog: CharacterTemplateCatalog,
) {
  fun snapshotFor(playerId: UUID): PlayerStateSnapshot {
    val player =
      playerRepository.findById(playerId)
        ?: throw ResourceNotFoundException("Player $playerId was not found.")
    val inventory = inventoryRepository.findByPlayerId(playerId)

    return PlayerStateSnapshot(
      playerId = player.id,
      playerName = player.name,
      ownedCharacters =
        player.ownedCharacterKeys
          .sorted()
          .map { key ->
            val template = characterTemplateCatalog.require(key)
            OwnedCharacterSnapshot(key = template.key, name = template.name)
          },
      inventory =
        inventory.map {
          InventoryItemSnapshot(
            id = it.id,
            itemName = it.item.name,
            itemDisplayName = it.item.displayName,
            itemType = it.item.type,
            itemBaseStat = it.item.baseStat,
            itemSubStatPool = it.item.subStatPool,
            subStats = it.subStats,
            rarity = it.rarity,
            upgrade = it.upgrade,
            equippedCharacterKey = it.equippedCharacterKey,
          )
        },
      serverTime = Instant.now(),
    )
  }

  fun combatSnapshotFor(playerId: UUID) = combatStateRepository.findById(playerId)?.toSnapshot()
}
