package com.wafuri.idle.tests.application

import com.wafuri.idle.application.config.GameConfig
import com.wafuri.idle.application.port.out.ActivePlayerRegistry
import com.wafuri.idle.application.port.out.CombatStateRepository
import com.wafuri.idle.application.port.out.PlayerMessageQueue
import com.wafuri.idle.application.port.out.PlayerStateWorkQueue
import com.wafuri.idle.application.service.combat.CombatLootService
import com.wafuri.idle.application.service.combat.CombatStatService
import com.wafuri.idle.application.service.combat.CombatTickService
import com.wafuri.idle.application.service.combat.RandomSource
import com.wafuri.idle.application.service.enemy.EnemyTemplateCatalog
import com.wafuri.idle.application.service.player.ProgressionService
import com.wafuri.idle.application.service.scaling.ScalingRule
import com.wafuri.idle.application.service.zone.ZoneTemplateCatalog
import com.wafuri.idle.domain.model.CombatState
import com.wafuri.idle.domain.model.CombatStatus
import com.wafuri.idle.domain.model.LevelRange
import com.wafuri.idle.domain.model.ZoneTemplate
import com.wafuri.idle.tests.support.expectedCombatMemberState
import com.wafuri.idle.tests.support.expectedCombatState
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
import kotlinx.coroutines.runBlocking
import java.time.Duration
import java.util.UUID

class CombatTickServiceTest : StringSpec() {
  private lateinit var activePlayerRegistry: ActivePlayerRegistry
  private lateinit var combatStateRepository: CombatStateRepository
  private lateinit var combatStatService: CombatStatService
  private lateinit var playerMessageQueue: PlayerMessageQueue
  private lateinit var playerStateWorkQueue: PlayerStateWorkQueue
  private lateinit var combatLootService: CombatLootService
  private lateinit var progressionService: ProgressionService
  private lateinit var zoneTemplateCatalog: ZoneTemplateCatalog
  private lateinit var enemyTemplateCatalog: EnemyTemplateCatalog
  private lateinit var randomSource: RandomSource
  private lateinit var config: GameConfig
  private lateinit var service: CombatTickService

  init {
    beforeTest {
      activePlayerRegistry = mockk()
      combatStateRepository = mockk()
      combatStatService = mockk()
      playerMessageQueue = mockk(relaxed = true)
      playerStateWorkQueue = mockk()
      combatLootService = mockk()
      progressionService = mockk()
      zoneTemplateCatalog = mockk()
      enemyTemplateCatalog = mockk()
      randomSource = mockk()
      config =
        gameConfig(
          damageInterval = Duration.ofSeconds(1),
          respawnDelay = Duration.ofSeconds(1),
        )
      service =
        CombatTickService(
          activePlayerRegistry,
          combatStateRepository,
          combatStatService,
          playerMessageQueue,
          playerStateWorkQueue,
          combatLootService,
          progressionService,
          zoneTemplateCatalog,
          enemyTemplateCatalog,
          randomSource,
          ScalingRule(config),
          config,
        )
    }

    "tick advances enemy hp when enough elapsed time has accumulated" {
      val playerId = UUID.randomUUID()
      val teamId = UUID.randomUUID()
      val current =
        expectedSingleMemberCombatState(
          playerId = playerId,
          teamId = teamId,
          attack = 12f,
          hit = 7f,
          currentHp = 11f,
          maxHp = 11f,
          enemyHp = 1000f,
          enemyMaxHp = 1000f,
        )
      var savedState: CombatState? = null

      every { combatStateRepository.findActiveByZoneId("starter-plains") } returns listOf(current)
      every { activePlayerRegistry.activePlayerIds() } returns setOf(playerId)
      every { combatStateRepository.findById(playerId) } returns current
      every { combatStatService.teamStatsForPlayerOrNull(playerId, current.members) } returns
        expectedSingleMemberTeamCombatStats(teamId = teamId, attack = 12f, hit = 7f, maxHp = 11f)
      every { combatStatService.teamSkillsForPlayerOrNull(playerId) } returns emptyMap()
      every { combatStateRepository.save(any()) } answers {
        firstArg<CombatState>().also { savedState = it }
      }
      every { playerStateWorkQueue.markDirty(playerId) } just runs
      every { combatLootService.rollLoot(any(), any(), any()) } returns null
      every { progressionService.recordKill(any(), any(), any()) } returns Unit
      every { enemyTemplateCatalog.requireRandom(listOf("training-dummy"), randomSource) } returns trainingDummyEnemy()

      runBlocking {
        service.tickZone("starter-plains", Duration.ofMillis(200))
      }

      savedState shouldBe
        expectedCombatState(
          playerId = playerId,
          status = CombatStatus.FIGHTING,
          zoneId = "starter-plains",
          activeTeamId = teamId,
          enemyName = "Training Dummy",
          enemyHp = 1000f,
          enemyMaxHp = 1000f,
          members = listOf(expectedCombatMemberState("warrior", 12f, 7f, 11f, 11f)),
          pendingDamageMillis = 200L,
          lastSimulatedAt = savedState?.lastSimulatedAt,
        )
      verify(exactly = 0) { playerStateWorkQueue.markDirty(playerId) }

      runBlocking {
        service.tickZone("starter-plains", Duration.ofSeconds(1))
      }

      savedState shouldBe
        expectedCombatState(
          playerId = playerId,
          status = CombatStatus.FIGHTING,
          zoneId = "starter-plains",
          activeTeamId = teamId,
          enemyName = "Training Dummy",
          enemyHp = 916f,
          enemyMaxHp = 1000f,
          members = listOf(expectedCombatMemberState("warrior", 12f, 7f, 10f, 11f)),
          lastSimulatedAt = savedState?.lastSimulatedAt,
        )
      verify(exactly = 1) { playerStateWorkQueue.markDirty(playerId) }
    }

    "tick resets combat to idle when the player no longer has a combat-ready team" {
      val playerId = UUID.randomUUID()
      val teamId = UUID.randomUUID()
      val current =
        expectedSingleMemberCombatState(
          playerId = playerId,
          teamId = teamId,
          attack = 12f,
          hit = 7f,
          currentHp = 11f,
          maxHp = 11f,
          enemyHp = 1000f,
          enemyMaxHp = 1000f,
        )
      var savedState: CombatState? = null

      every { combatStateRepository.findActiveByZoneId("starter-plains") } returns listOf(current)
      every { activePlayerRegistry.activePlayerIds() } returns setOf(playerId)
      every { combatStateRepository.findById(playerId) } returns current
      every { combatStatService.teamStatsForPlayerOrNull(playerId, current.members) } returns null
      every { combatStatService.teamSkillsForPlayerOrNull(playerId) } returns emptyMap()
      every { combatStateRepository.save(any()) } answers {
        firstArg<CombatState>().also { savedState = it }
      }
      every { playerStateWorkQueue.markDirty(playerId) } just runs
      every { enemyTemplateCatalog.requireRandom(listOf("training-dummy"), randomSource) } returns trainingDummyEnemy()

      runBlocking {
        service.tickZone("starter-plains", Duration.ofMillis(200))
      }

      savedState shouldBe CombatState.idle(playerId, lastSimulatedAt = savedState?.lastSimulatedAt)
      verify(exactly = 1) { playerStateWorkQueue.markDirty(playerId) }
    }

    "tick respawns a random enemy from the current zone pool after a win" {
      val playerId = UUID.randomUUID()
      val teamId = UUID.randomUUID()
      val zone =
        ZoneTemplate(
          "starter-plains",
          "Starter Plains",
          LevelRange(1, 10),
          emptyList(),
          emptyList(),
          listOf("training-dummy", "straw-golem"),
        )
      val current =
        expectedSingleMemberCombatState(
          playerId = playerId,
          teamId = teamId,
          attack = 150f,
          hit = 1f,
          currentHp = 100f,
          maxHp = 100f,
          enemyHp = 1f,
          enemyMaxHp = 1f,
          enemyAttack = 1f,
        )
      var currentState = current
      var savedState: CombatState? = null

      every { combatStateRepository.findActiveByZoneId(zone.id) } answers { listOf(currentState) }
      every { activePlayerRegistry.activePlayerIds() } returns setOf(playerId)
      every { combatStateRepository.findById(playerId) } answers { currentState }
      every { combatStatService.teamStatsForPlayerOrNull(playerId, any()) } returns
        expectedSingleMemberTeamCombatStats(teamId = teamId, attack = 150f, hit = 1f, maxHp = 100f)
      every { combatStatService.teamSkillsForPlayerOrNull(playerId) } returns emptyMap()
      every { combatStateRepository.save(any()) } answers {
        firstArg<CombatState>().also {
          currentState = it
          savedState = it
        }
      }
      every { playerStateWorkQueue.markDirty(playerId) } just runs
      every { combatLootService.rollLoot(playerId, zone.id, any()) } returns null
      every { progressionService.recordKill(playerId, zone.id, any()) } returns Unit
      every { progressionService.requireZoneProgress(playerId, zone.id) } returns mockk { every { level } returns 1 }
      every { zoneTemplateCatalog.require(zone.id) } returns zone
      every { enemyTemplateCatalog.requireRandom(zone.enemies, randomSource) } returns strawGolemEnemy()

      runBlocking {
        service.tickZone(zone.id, Duration.ofSeconds(1))
      }
      runBlocking {
        service.tickZone(zone.id, Duration.ofSeconds(1))
      }

      savedState?.status shouldBe CombatStatus.FIGHTING
      savedState?.enemyId shouldBe "straw-golem"
      savedState?.enemyName shouldBe "Straw Golem"
      savedState?.enemyAttack shouldBe 2f
      savedState?.enemyHp shouldBe 1200f
      savedState?.enemyMaxHp shouldBe 1200f
    }
  }
}
