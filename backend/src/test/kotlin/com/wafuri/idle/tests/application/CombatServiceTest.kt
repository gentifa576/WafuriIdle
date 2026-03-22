package com.wafuri.idle.tests.application

import com.wafuri.idle.application.config.GameConfig
import com.wafuri.idle.application.model.CharacterCombatStats
import com.wafuri.idle.application.model.TeamCombatStats
import com.wafuri.idle.application.port.out.CombatStateRepository
import com.wafuri.idle.application.port.out.PlayerStateWorkQueue
import com.wafuri.idle.application.port.out.Repository
import com.wafuri.idle.application.service.combat.CombatService
import com.wafuri.idle.application.service.combat.CombatStatService
import com.wafuri.idle.application.service.zone.ZoneTemplateCatalog
import com.wafuri.idle.domain.model.CombatState
import com.wafuri.idle.domain.model.CombatStatus
import com.wafuri.idle.domain.model.LevelRange
import com.wafuri.idle.domain.model.Player
import com.wafuri.idle.domain.model.ZoneTemplate
import com.wafuri.idle.tests.support.gameConfig
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import java.util.UUID

class CombatServiceTest : StringSpec() {
  private lateinit var playerRepository: Repository<Player, UUID>
  private lateinit var combatStateRepository: CombatStateRepository
  private lateinit var combatStatService: CombatStatService
  private lateinit var playerStateWorkQueue: PlayerStateWorkQueue
  private lateinit var zoneTemplateCatalog: ZoneTemplateCatalog
  private lateinit var config: GameConfig
  private lateinit var service: CombatService

  init {
    beforeTest {
      playerRepository = mockk()
      combatStateRepository = mockk()
      combatStatService = mockk()
      playerStateWorkQueue = mockk()
      zoneTemplateCatalog = mockk()
      config = gameConfig(enemyMaxHp = 1000f)
      service =
        CombatService(
          playerRepository,
          combatStateRepository,
          combatStatService,
          playerStateWorkQueue,
          zoneTemplateCatalog,
          config,
        )
    }

    "start combat uses the player's current team stats" {
      val playerId = UUID.randomUUID()
      val teamId = UUID.randomUUID()
      val player = Player(playerId, "Alice")
      val teamStats =
        TeamCombatStats(
          teamId = teamId,
          characterStats =
            listOf(
              CharacterCombatStats("warrior", 12f, 7f, 11f),
            ),
        )
      var savedState: CombatState? = null
      val zone =
        ZoneTemplate(
          id = "starter-plains",
          name = "Starter Plains",
          levelRange = LevelRange(1, 10),
          eventRefs = emptyList(),
          lootTable = emptyList(),
          enemies = listOf("Training Dummy"),
        )

      every { playerRepository.findById(playerId) } returns player
      every { combatStatService.teamStatsForPlayer(playerId) } returns teamStats
      every { zoneTemplateCatalog.default() } returns zone
      every { combatStateRepository.findById(playerId) } returns null
      every { combatStateRepository.save(any()) } answers {
        firstArg<CombatState>().also { savedState = it }
      }
      every { playerStateWorkQueue.markDirty(playerId) } just runs

      val result = service.start(playerId)

      result.status shouldBe CombatStatus.FIGHTING
      result.zoneId shouldBe zone.id
      result.activeTeamId shouldBe teamId
      result.enemyName shouldBe "Training Dummy"
      result.enemyHp shouldBe 1000f
      result.teamDps shouldBe (84f plusOrMinus 0.001f)
      result.members.single().currentHp shouldBe 11f
      result.members.single().maxHp shouldBe 11f
      savedState?.status shouldBe CombatStatus.FIGHTING
      verify(exactly = 1) { playerStateWorkQueue.markDirty(playerId) }
    }
  }
}
