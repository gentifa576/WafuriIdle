package com.wafuri.idle.tests.persistence

import com.wafuri.idle.application.model.ClusterNode
import com.wafuri.idle.application.port.out.ClusterNodeRepository
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
import java.time.Instant
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

  @Inject
  lateinit var clusterNodeRepository: ClusterNodeRepository

  @Test
  @TestTransaction
  fun `repositories persist and reload aggregates`() {
    val player = playerRepository.save(Player(UUID.randomUUID(), "Alice", activeTeamId = UUID.randomUUID()))
    val team = teamRepository.save(Team(UUID.randomUUID(), player.id))
    val updatedTeam = teamRepository.save(team.assignCharacter(1, "warrior"))
    val item = itemRepository.save(swordItem())
    val inventoryItem =
      inventoryRepository.save(
        InventoryItem(
          id = UUID.randomUUID(),
          playerId = player.id,
          item = item,
          equippedTeamId = team.id,
          equippedPosition = 1,
        ),
      )

    playerRepository.findById(player.id)?.activeTeamId shouldBe player.activeTeamId
    teamRepository.findByPlayerId(player.id).single().id shouldBe team.id
    updatedTeam.characterKeys.single() shouldBe "warrior"
    inventoryRepository.findByTeamPositionAndSlot(team.id, 1, EquipmentSlot.WEAPON)?.equippedTeamId shouldBe team.id
    inventoryRepository.findById(inventoryItem.id)?.item?.name shouldBe item.name
  }

  @Test
  @TestTransaction
  fun `cluster node repository persists and queries live nodes`() {
    val now = Instant.now()
    clusterNodeRepository.save(
      ClusterNode(
        instanceId = "node-a",
        internalBaseUrl = "http://10.0.0.1:8080",
        lastHeartbeatAt = now,
      ),
    )

    clusterNodeRepository.findAliveSince(now.minusSeconds(1)).any { it.instanceId == "node-a" } shouldBe true
  }
}
