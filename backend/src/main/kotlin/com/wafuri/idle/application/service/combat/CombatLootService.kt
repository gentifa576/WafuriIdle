package com.wafuri.idle.application.service.combat

import com.wafuri.idle.application.config.GameConfig
import com.wafuri.idle.application.service.inventory.InventoryService
import com.wafuri.idle.application.service.item.ItemTemplateCatalog
import com.wafuri.idle.application.service.zone.ZoneTemplateCatalog
import com.wafuri.idle.domain.model.InventoryItem
import com.wafuri.idle.domain.model.Rarity
import com.wafuri.idle.domain.model.ZoneLootEntry
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

@ApplicationScoped
class CombatLootService(
  private val zoneTemplateCatalog: ZoneTemplateCatalog,
  private val itemTemplateCatalog: ItemTemplateCatalog,
  private val inventoryService: InventoryService,
  private val gameConfig: GameConfig,
  private val randomSource: RandomSource,
) {
  fun rollLoot(
    playerId: UUID,
    zoneId: String,
  ): InventoryItem? {
    val lootConfig = gameConfig.combat().loot()
    if (randomSource.nextFloat() >= lootConfig.baseItemDropRate()) {
      return null
    }

    val zone = zoneTemplateCatalog.require(zoneId)
    val rolledEntry = rollZoneLoot(zone.lootTable) ?: return null
    val item = itemTemplateCatalog.require(rolledEntry.itemName)
    return inventoryService.addGeneratedItem(playerId, item.name, rollRarity())
  }

  private fun rollZoneLoot(lootTable: List<ZoneLootEntry>): ZoneLootEntry? {
    if (lootTable.isEmpty()) {
      return null
    }

    val totalWeight = lootTable.sumOf { it.weight }
    if (totalWeight <= 0) {
      return null
    }

    var remaining = randomSource.nextInt(totalWeight)
    lootTable.forEach { entry ->
      remaining -= entry.weight
      if (remaining < 0) {
        return entry
      }
    }
    return lootTable.last()
  }

  private fun rollRarity(): Rarity {
    val rarityConfig = gameConfig.combat().loot().rarity()
    val entries =
      listOf(
        Rarity.COMMON to rarityConfig.common(),
        Rarity.RARE to rarityConfig.rare(),
        Rarity.EPIC to rarityConfig.epic(),
        Rarity.LEGENDARY to rarityConfig.legendary(),
      )
    val totalWeight = entries.sumOf { it.second.toDouble() }.toFloat()
    if (totalWeight <= 0f) {
      return Rarity.COMMON
    }

    var remaining = randomSource.nextFloat() * totalWeight
    entries.forEach { (rarity, weight) ->
      remaining -= weight
      if (remaining < 0f) {
        return rarity
      }
    }
    return Rarity.LEGENDARY
  }
}
