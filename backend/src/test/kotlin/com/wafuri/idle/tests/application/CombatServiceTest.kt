package com.wafuri.idle.tests.application

import com.wafuri.idle.application.config.GameConfig
import com.wafuri.idle.application.port.out.CombatStateRepository
import com.wafuri.idle.application.port.out.PlayerStateWorkQueue
import com.wafuri.idle.application.port.out.Repository
import com.wafuri.idle.application.service.combat.CombatService
import com.wafuri.idle.application.service.combat.CombatStatService
import com.wafuri.idle.application.service.combat.RandomSource
import com.wafuri.idle.application.service.enemy.EnemyTemplateCatalog
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
import com.wafuri.idle.tests.support.strawGolemEnemy
import com.wafuri.idle.tests.support.trainingDummyEnemy
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
  private lateinit var enemyTemplateCatalog: EnemyTemplateCatalog
  private lateinit var randomSource: RandomSource
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
      enemyTemplateCatalog = mockk()
      randomSource = mockk()
      progressionService = mockk()
      config = gameConfig()
      service =
        CombatService(
          playerRepository,
          combatStateRepository,
          combatStatService,
          playerStateWorkQueue,
          zoneTemplateCatalog,
          enemyTemplateCatalog,
          randomSource,
          progressionService,
          ScalingRule(config),
        )
    }

    "start combat uses the player's current team stats" {
      val playerId = UUID.randomUUID()
      val teamId = UUID.randomUUID()
      val player = expectedPlayer(playerId, "Alice")
      val teamStats = expectedSingleMemberTeamCombatStats(teamId, attack = 12f, hit = 7f, maxHp = 11f)
      var savedState: CombatState? = null
      val zone =
        ZoneTemplate(
          "starter-plains",
          "Starter Plains",
          LevelRange(1, 10),
          emptyList(),
          emptyList(),
          listOf("training-dummy"),
        )
      val enemy = trainingDummyEnemy()

      every { playerRepository.require(playerId) } returns player
      every { combatStatService.teamStatsForPlayer(playerId) } returns teamStats
      every { combatStatService.teamSkillsForPlayerOrNull(playerId) } returns emptyMap()
      every { zoneTemplateCatalog.default() } returns zone
      every { enemyTemplateCatalog.requireRandom(zone.enemies, randomSource) } returns enemy
      every { progressionService.requireZoneProgress(playerId, zone.id) } returns mockk { every { level } returns 1 }
      every { combatStateRepository.findById(playerId) } returns null
      every { combatStateRepository.save(any()) } answers {
        firstArg<CombatState>().also { savedState = it }
      }
      every { playerStateWorkQueue.markDirty(playerId) } just runs

      val result = service.start(playerId)
      val expectedState =
        expectedSingleMemberCombatState(
          playerId,
          teamId,
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
          playerId,
          CombatStatus.FIGHTING,
          zone.id,
          teamId,
          "Training Dummy",
          1f,
          1000f,
          1000f,
          84f,
          members =
            listOf(
              expectedCombatMemberSnapshot(
                "warrior",
                12f,
                7f,
                11f,
                11f,
                true,
              ),
            ),
        )

      result shouldBe expectedSnapshot
      savedState shouldBe expectedState
      verify(exactly = 1) { playerStateWorkQueue.markDirty(playerId) }
    }

    "start combat selects a random enemy from the zone pool" {
      val playerId = UUID.randomUUID()
      val teamId = UUID.randomUUID()
      val player = expectedPlayer(playerId, "Alice")
      val teamStats = expectedSingleMemberTeamCombatStats(teamId, attack = 12f, hit = 7f, maxHp = 11f)
      val zone =
        ZoneTemplate(
          "starter-plains",
          "Starter Plains",
          LevelRange(1, 10),
          emptyList(),
          emptyList(),
          listOf("training-dummy", "straw-golem"),
        )

      every { playerRepository.require(playerId) } returns player
      every { combatStatService.teamStatsForPlayer(playerId) } returns teamStats
      every { combatStatService.teamSkillsForPlayerOrNull(playerId) } returns emptyMap()
      every { zoneTemplateCatalog.default() } returns zone
      every { enemyTemplateCatalog.requireRandom(zone.enemies, randomSource) } returns strawGolemEnemy()
      every { progressionService.requireZoneProgress(playerId, zone.id) } returns mockk { every { level } returns 1 }
      every { combatStateRepository.findById(playerId) } returns null
      every { combatStateRepository.save(any()) } answers { firstArg<CombatState>() }
      every { playerStateWorkQueue.markDirty(playerId) } just runs

      val result = service.start(playerId)

      result.enemyName shouldBe "Straw Golem"
      result.enemyAttack shouldBe 2f
      result.enemyHp shouldBe 1200f
      result.enemyMaxHp shouldBe 1200f
      verify(exactly = 1) { enemyTemplateCatalog.requireRandom(zone.enemies, randomSource) }
    }

    "stop combat resets the player to idle combat state" {
      val playerId = UUID.randomUUID()
      val player = expectedPlayer(playerId, "Alice")
      var savedState: CombatState? = null

      every { playerRepository.require(playerId) } returns player
      every { combatStateRepository.save(any()) } answers {
        firstArg<CombatState>().also { savedState = it }
      }
      every { playerStateWorkQueue.markDirty(playerId) } just runs

      val result = service.stop(playerId)

      result shouldBe null
      savedState shouldBe CombatState.idle(playerId)
      verify(exactly = 1) { playerStateWorkQueue.markDirty(playerId) }
    }
  }
}
