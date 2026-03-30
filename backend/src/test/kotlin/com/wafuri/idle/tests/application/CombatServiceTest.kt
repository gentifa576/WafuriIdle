package com.wafuri.idle.tests.application

import com.wafuri.idle.application.config.GameConfig
import com.wafuri.idle.application.port.out.CombatStateRepository
import com.wafuri.idle.application.port.out.PlayerStateWorkQueue
import com.wafuri.idle.application.port.out.Repository
import com.wafuri.idle.application.service.combat.CombatService
import com.wafuri.idle.application.service.combat.CombatStatService
import com.wafuri.idle.application.service.player.ProgressionService
import com.wafuri.idle.application.service.scaling.ScalingRule
import com.wafuri.idle.application.service.zone.ZoneTemplateCatalog
import com.wafuri.idle.domain.model.CombatState
import com.wafuri.idle.domain.model.CombatStatus
import com.wafuri.idle.domain.model.LevelRange
import com.wafuri.idle.domain.model.Player
import com.wafuri.idle.domain.model.ZoneTemplate
import com.wafuri.idle.tests.support.expectedCombatMemberSnapshot
import com.wafuri.idle.tests.support.expectedCombatSnapshot
import com.wafuri.idle.tests.support.expectedPlayer
import com.wafuri.idle.tests.support.expectedSingleMemberCombatState
import com.wafuri.idle.tests.support.expectedSingleMemberTeamCombatStats
import com.wafuri.idle.tests.support.gameConfig
import io.kotest.core.spec.style.StringSpec
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
  private lateinit var progressionService: ProgressionService
  private lateinit var config: GameConfig
  private lateinit var service: CombatService

  init {
    beforeTest {
      playerRepository = mockk()
      combatStateRepository = mockk()
      combatStatService = mockk()
      playerStateWorkQueue = mockk()
      zoneTemplateCatalog = mockk()
      progressionService = mockk()
      config = gameConfig(enemyMaxHp = 1000f)
      service =
        CombatService(
          playerRepository,
          combatStateRepository,
          combatStatService,
          playerStateWorkQueue,
          zoneTemplateCatalog,
          progressionService,
          ScalingRule(config),
          config,
        )
    }

    "start combat uses the player's current team stats" {
      val playerId = UUID.randomUUID()
      val teamId = UUID.randomUUID()
      val player = expectedPlayer(id = playerId, name = "Alice")
      val teamStats = expectedSingleMemberTeamCombatStats(teamId = teamId, attack = 12f, hit = 7f, maxHp = 11f)
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
      every { progressionService.requireZoneProgress(playerId, zone.id) } returns mockk { every { level } returns 1 }
      every { combatStateRepository.findById(playerId) } returns null
      every { combatStateRepository.save(any()) } answers {
        firstArg<CombatState>().also { savedState = it }
      }
      every { playerStateWorkQueue.markDirty(playerId) } just runs

      val result = service.start(playerId)
      val expectedState =
        expectedSingleMemberCombatState(
          playerId = playerId,
          teamId = teamId,
          attack = 12f,
          hit = 7f,
          currentHp = 11f,
          maxHp = 11f,
          enemyHp = 1000f,
          enemyMaxHp = 1000f,
          zoneId = zone.id,
          lastSimulatedAt = savedState?.lastSimulatedAt,
        )
      val expectedSnapshot =
        expectedCombatSnapshot(
          playerId = playerId,
          status = CombatStatus.FIGHTING,
          zoneId = zone.id,
          activeTeamId = teamId,
          enemyName = "Training Dummy",
          enemyHp = 1000f,
          enemyMaxHp = 1000f,
          teamDps = 84f,
          members =
            listOf(
              expectedCombatMemberSnapshot(
                characterKey = "warrior",
                attack = 12f,
                hit = 7f,
                currentHp = 11f,
                maxHp = 11f,
                alive = true,
              ),
            ),
        )

      result shouldBe expectedSnapshot
      savedState shouldBe expectedState
      verify(exactly = 1) { playerStateWorkQueue.markDirty(playerId) }
    }
  }
}
