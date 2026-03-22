package com.wafuri.idle.tests.application

import com.wafuri.idle.application.exception.ValidationException
import com.wafuri.idle.application.port.out.InventoryRepository
import com.wafuri.idle.application.port.out.PlayerStateWorkQueue
import com.wafuri.idle.application.port.out.Repository
import com.wafuri.idle.application.service.inventory.EquipmentService
import com.wafuri.idle.domain.model.EquipmentSlot
import com.wafuri.idle.domain.model.InventoryItem
import com.wafuri.idle.domain.model.Player
import com.wafuri.idle.tests.support.swordItem
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import java.util.UUID

class EquipmentServiceTest : StringSpec() {
  private lateinit var playerRepository: Repository<Player, UUID>
  private lateinit var inventoryRepository: InventoryRepository
  private lateinit var playerStateWorkQueue: PlayerStateWorkQueue
  private lateinit var service: EquipmentService

  init {
    beforeTest {
      playerRepository = mockk()
      inventoryRepository = mockk()
      playerStateWorkQueue = mockk()
      service = EquipmentService(playerRepository, inventoryRepository, playerStateWorkQueue)
    }

    "equip validates ownership and slot" {
      val playerId = UUID.randomUUID()
      val characterKey = "warrior"
      val player = Player(playerId, "Alice", ownedCharacterKeys = setOf(characterKey))
      val foreignItem =
        InventoryItem(
          id = UUID.randomUUID(),
          playerId = UUID.randomUUID(),
          item = swordItem(),
        )

      every { inventoryRepository.findById(foreignItem.id) } returns foreignItem
      every { playerRepository.findById(foreignItem.playerId) } returns player
      every { inventoryRepository.findByCharacterAndSlot(characterKey, EquipmentSlot.WEAPON) } returns null

      shouldThrow<ValidationException> {
        service.equip(characterKey, foreignItem.id, EquipmentSlot.WEAPON)
      }
    }

    "unequip returns item to inventory" {
      val playerId = UUID.randomUUID()
      val inventoryItemId = UUID.randomUUID()
      val characterKey = "warrior"
      val player = Player(playerId, "Alice", ownedCharacterKeys = setOf(characterKey))
      val inventoryItem =
        InventoryItem(
          id = inventoryItemId,
          playerId = playerId,
          item = swordItem(),
          equippedCharacterKey = characterKey,
        )
      val savedItems = mutableListOf<InventoryItem>()

      every { inventoryRepository.findByCharacterAndSlot(characterKey, EquipmentSlot.WEAPON) } returns inventoryItem
      every { playerRepository.findById(playerId) } returns player
      every { inventoryRepository.save(any()) } answers {
        firstArg<InventoryItem>().also { savedItems += it }
      }
      every { playerStateWorkQueue.markDirty(playerId) } just runs

      service.unequip(characterKey, EquipmentSlot.WEAPON)

      savedItems.last().equippedCharacterKey.shouldBeNull()
      verify(exactly = 1) { playerStateWorkQueue.markDirty(playerId) }
    }
  }
}
