package com.wafuri.idle.tests.application

import com.wafuri.idle.application.port.out.InventoryRepository
import com.wafuri.idle.application.port.out.PlayerStateWorkQueue
import com.wafuri.idle.application.port.out.Repository
import com.wafuri.idle.application.service.inventory.InventoryService
import com.wafuri.idle.application.service.item.ItemTemplateCatalog
import com.wafuri.idle.domain.model.InventoryItem
import com.wafuri.idle.domain.model.Player
import com.wafuri.idle.domain.model.Rarity
import com.wafuri.idle.tests.support.swordItem
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import java.util.UUID

class InventoryServiceTest : StringSpec() {
  private lateinit var playerRepository: Repository<Player, UUID>
  private lateinit var itemTemplateCatalog: ItemTemplateCatalog
  private lateinit var inventoryRepository: InventoryRepository
  private lateinit var playerStateWorkQueue: PlayerStateWorkQueue
  private lateinit var service: InventoryService

  init {
    beforeTest {
      playerRepository = mockk()
      itemTemplateCatalog = mockk()
      inventoryRepository = mockk()
      playerStateWorkQueue = mockk()
      service = InventoryService(playerRepository, itemTemplateCatalog, inventoryRepository, playerStateWorkQueue)
    }

    "add generated item stores owned inventory item" {
      val player = Player(UUID.randomUUID(), "Alice")

      every { playerRepository.findById(player.id) } returns player
      every { itemTemplateCatalog.require("sword_0001") } returns swordItem()
      every { inventoryRepository.save(any()) } answers { firstArg<InventoryItem>() }
      every { playerStateWorkQueue.markDirty(player.id) } just runs

      val inventoryItem = service.addGeneratedItem(player.id, "sword_0001", Rarity.COMMON)

      inventoryItem.playerId shouldBe player.id
      inventoryItem.rarity shouldBe Rarity.COMMON
      verify(exactly = 1) { inventoryRepository.save(any()) }
      verify(exactly = 1) { playerStateWorkQueue.markDirty(player.id) }
    }
  }
}
