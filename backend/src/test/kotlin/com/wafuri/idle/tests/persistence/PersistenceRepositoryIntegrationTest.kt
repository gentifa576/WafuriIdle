package com.wafuri.idle.tests.persistence

import com.wafuri.idle.application.port.out.InventoryRepository
import com.wafuri.idle.application.port.out.ItemTemplateRepository
import com.wafuri.idle.application.port.out.Repository
import com.wafuri.idle.application.port.out.TeamRepository
import com.wafuri.idle.domain.model.EquipmentSlot
import com.wafuri.idle.domain.model.InventoryItem
import com.wafuri.idle.domain.model.Player
import com.wafuri.idle.domain.model.Team
import com.wafuri.idle.tests.support.swordItem
import io.kotest.matchers.shouldBe
import io.quarkus.test.TestTransaction
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Test
import java.util.UUID

@QuarkusTest
class PersistenceRepositoryIntegrationTest {
  @Inject
  lateinit var playerRepository: Repository<Player, UUID>

  @Inject
  lateinit var teamRepository: TeamRepository

  @Inject
  lateinit var itemRepository: ItemTemplateRepository

  @Inject
  lateinit var inventoryRepository: InventoryRepository

  @Test
  @TestTransaction
  fun `repositories persist and reload aggregates`() {
    val player = playerRepository.save(Player(UUID.randomUUID(), "Alice", activeTeamId = UUID.randomUUID()))
    val team = teamRepository.save(Team(UUID.randomUUID(), player.id))
    val updatedTeam = teamRepository.save(team.addCharacter("warrior"))
    val item = itemRepository.save(swordItem())
    val inventoryItem =
      inventoryRepository.save(
        InventoryItem(
          id = UUID.randomUUID(),
          playerId = player.id,
          item = item,
          equippedCharacterKey = "warrior",
        ),
      )

    playerRepository.findById(player.id)?.activeTeamId shouldBe player.activeTeamId
    teamRepository.findByPlayerId(player.id).single().id shouldBe team.id
    updatedTeam.characterKeys.single() shouldBe "warrior"
    inventoryRepository.findByCharacterAndSlot("warrior", EquipmentSlot.WEAPON)?.equippedCharacterKey shouldBe "warrior"
    inventoryRepository.findById(inventoryItem.id)?.item?.name shouldBe item.name
  }
}
