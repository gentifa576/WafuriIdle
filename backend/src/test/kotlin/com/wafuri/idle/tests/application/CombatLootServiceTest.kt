package com.wafuri.idle.tests.application

import com.wafuri.idle.application.config.GameConfig
import com.wafuri.idle.application.service.combat.CombatLootService
import com.wafuri.idle.application.service.combat.RandomSource
import com.wafuri.idle.application.service.inventory.InventoryService
import com.wafuri.idle.application.service.item.ItemTemplateCatalog
import com.wafuri.idle.application.service.zone.ZoneTemplateCatalog
import com.wafuri.idle.domain.model.LevelRange
import com.wafuri.idle.domain.model.Rarity
import com.wafuri.idle.domain.model.ZoneLootEntry
import com.wafuri.idle.domain.model.ZoneTemplate
import com.wafuri.idle.tests.support.gameConfig
import com.wafuri.idle.tests.support.swordItem
import io.kotest.core.spec.style.StringSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.UUID

class CombatLootServiceTest : StringSpec() {
  private lateinit var zoneTemplateCatalog: ZoneTemplateCatalog
  private lateinit var itemTemplateCatalog: ItemTemplateCatalog
  private lateinit var inventoryService: InventoryService
  private lateinit var randomSource: RandomSource
  private lateinit var config: GameConfig
  private lateinit var service: CombatLootService

  private fun lootZone(): ZoneTemplate =
    ZoneTemplate(
      id = "starter-plains",
      name = "Starter Plains",
      levelRange = LevelRange(1, 10),
      lootTable =
        listOf(
          ZoneLootEntry(itemName = "sword_0001", weight = 70),
          ZoneLootEntry(itemName = "shield_0001", weight = 30),
        ),
      enemies = listOf("Training Dummy"),
      eventRefs = emptyList(),
    )

  init {
    beforeTest {
      zoneTemplateCatalog = mockk()
      itemTemplateCatalog = mockk()
      inventoryService = mockk()
      randomSource = mockk()
      config = gameConfig()
      service =
        CombatLootService(
          zoneTemplateCatalog,
          itemTemplateCatalog,
          inventoryService,
          config,
          randomSource,
        )
    }

    "roll loot grants a weighted item with rolled rarity when the drop succeeds" {
      val playerId = UUID.randomUUID()
      val zone = lootZone()

      every { randomSource.nextFloat() } returnsMany listOf(0.001f, 0.75f)
      every { randomSource.nextInt(100) } returns 20
      every { zoneTemplateCatalog.require("starter-plains") } returns zone
      every { itemTemplateCatalog.require("sword_0001") } returns swordItem()
      every { inventoryService.addGeneratedItem(playerId, "sword_0001", Rarity.COMMON) } answers { mockk() }

      service.rollLoot(playerId, "starter-plains")

      verify(exactly = 1) { inventoryService.addGeneratedItem(playerId, "sword_0001", Rarity.COMMON) }
    }

    "roll loot does nothing when the base drop rate misses" {
      val playerId = UUID.randomUUID()

      every { randomSource.nextFloat() } returns 0.5f

      service.rollLoot(playerId, "starter-plains")

      verify(exactly = 0) { zoneTemplateCatalog.require(any()) }
      verify(exactly = 0) { inventoryService.addGeneratedItem(any(), any(), any()) }
    }
  }
}
