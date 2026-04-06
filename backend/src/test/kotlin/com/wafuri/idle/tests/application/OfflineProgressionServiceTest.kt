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
import com.wafuri.idle.application.service.player.OfflineProgressionService
import com.wafuri.idle.application.service.player.ProgressionService
import com.wafuri.idle.application.service.scaling.ScalingRule
import com.wafuri.idle.application.service.zone.ZoneTemplateCatalog
import com.wafuri.idle.domain.model.CombatState
import com.wafuri.idle.domain.model.LevelRange
import com.wafuri.idle.domain.model.Player
import com.wafuri.idle.domain.model.PlayerZoneProgress
import com.wafuri.idle.domain.model.ZoneTemplate
import com.wafuri.idle.tests.support.expectedInventoryItem
import com.wafuri.idle.tests.support.expectedOfflineProgressionMessage
import com.wafuri.idle.tests.support.expectedOfflineProgressionResult
import com.wafuri.idle.tests.support.expectedOfflineRewardSummary
import com.wafuri.idle.tests.support.expectedPlayer
import com.wafuri.idle.tests.support.expectedSingleMemberCombatState
import com.wafuri.idle.tests.support.expectedSingleMemberTeamCombatStats
import com.wafuri.idle.tests.support.expectedZoneProgress
import com.wafuri.idle.tests.support.gameConfig
import com.wafuri.idle.tests.support.strawGolemEnemy
import com.wafuri.idle.tests.support.swordItem
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
import java.time.Instant
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt

class OfflineProgressionServiceTest : StringSpec() {
  private lateinit var combatStateRepository: CombatStateRepository
  private lateinit var combatStatService: CombatStatService
  private lateinit var progressionService: ProgressionService
  private lateinit var combatLootService: CombatLootService
  private lateinit var playerEventQueue: PlayerMessageQueue
  private lateinit var zoneTemplateCatalog: ZoneTemplateCatalog
  private lateinit var enemyTemplateCatalog: EnemyTemplateCatalog
  private lateinit var randomSource: RandomSource
  private lateinit var config: GameConfig
  private lateinit var service: OfflineProgressionService

  init {
    beforeTest {
      combatStateRepository = mockk()
      combatStatService = mockk()
      progressionService = mockk()
      combatLootService = mockk()
      playerEventQueue = mockk()
      zoneTemplateCatalog = mockk()
      enemyTemplateCatalog = mockk()
      randomSource = mockk()
      config =
        gameConfig(
          damageInterval = Duration.ofSeconds(1),
          respawnDelay = Duration.ofSeconds(1),
          offlineNotifyThreshold = Duration.ofMinutes(5),
        )
      service =
        OfflineProgressionService(
          combatStateRepository,
          combatStatService,
          progressionService,
          combatLootService,
          playerEventQueue,
          zoneTemplateCatalog,
          enemyTemplateCatalog,
          randomSource,
          ScalingRule(config),
          config,
        )
    }

    "applyIfNeeded initializes last simulated time when combat has never been simulated" {
      val playerId = UUID.randomUUID()
      val teamId = UUID.randomUUID()
      val state =
        expectedSingleMemberCombatState(
          playerId = playerId,
          teamId = teamId,
          attack = 10f,
          hit = 1f,
          currentHp = 1000f,
          maxHp = 1000f,
          enemyHp = 100f,
          enemyMaxHp = 100f,
          lastSimulatedAt = null,
        )

      every { combatStateRepository.findById(playerId) } returns state
      every { combatStateRepository.save(any()) } answers { firstArg() }
      every { enemyTemplateCatalog.requireRandom(listOf("training-dummy"), randomSource) } returns trainingDummyEnemy(baseHp = 100f)

      val result = service.applyIfNeeded(playerId)

      result shouldBe null
      verify(exactly = 1) { combatStateRepository.save(match { it.lastSimulatedAt != null }) }
      verify(exactly = 0) { progressionService.recordKill(any(), any(), any()) }
      verify(exactly = 0) { combatLootService.rollLoot(any(), any(), any()) }
      verify(exactly = 0) { playerEventQueue.enqueue(any()) }
    }

    "applyIfNeeded resets combat to idle when the player no longer has a combat-ready team" {
      val playerId = UUID.randomUUID()
      val teamId = UUID.randomUUID()
      val state =
        expectedSingleMemberCombatState(
          playerId = playerId,
          teamId = teamId,
          attack = 10f,
          hit = 1f,
          currentHp = 1000f,
          maxHp = 1000f,
          enemyHp = 100f,
          enemyMaxHp = 100f,
          lastSimulatedAt = Instant.now().minus(Duration.ofMinutes(1)),
        )

      every { combatStateRepository.findById(playerId) } returns state
      every { combatStatService.teamStatsForPlayerOrNull(playerId, state.members) } returns null
      every { combatStateRepository.save(any()) } answers { firstArg() }
      every { enemyTemplateCatalog.requireRandom(listOf("training-dummy"), randomSource) } returns trainingDummyEnemy(baseHp = 100f)

      val result = service.applyIfNeeded(playerId)

      result shouldBe null
      verify(exactly = 1) { combatStateRepository.save(match { it.status == com.wafuri.idle.domain.model.CombatStatus.IDLE }) }
      verify(exactly = 0) { progressionService.recordKill(any(), any(), any()) }
      verify(exactly = 0) { combatLootService.rollLoot(any(), any(), any()) }
      verify(exactly = 0) { playerEventQueue.enqueue(any()) }
    }

    "applyIfNeeded replays offline combat 1 to 1 and emits a summary after the notify threshold" {
      val playerId = UUID.randomUUID()
      val teamId = UUID.randomUUID()
      val zoneId = "starter-plains"
      val state =
        expectedSingleMemberCombatState(
          playerId = playerId,
          teamId = teamId,
          attack = 10f,
          hit = 1f,
          currentHp = 1000f,
          maxHp = 1000f,
          enemyHp = 100f,
          enemyMaxHp = 100f,
          lastSimulatedAt = Instant.now().minus(Duration.ofMinutes(6)),
        )
      val beforePlayer = expectedPlayer(playerId, "Alice")
      val afterPlayer = expectedPlayer(playerId, "Alice", experience = 320, level = 4, gold = 800)
      val beforeZone = expectedZoneProgress(playerId, zoneId)
      val afterZone = expectedZoneProgress(playerId, zoneId, 32, 4)
      val item =
        expectedInventoryItem(
          id = UUID.randomUUID(),
          playerId = playerId,
          item = swordItem(),
        )
      var savedState: CombatState? = null

      every { combatStateRepository.findById(playerId) } returns state
      every { combatStatService.teamStatsForPlayerOrNull(playerId, state.members) } returns
        expectedSingleMemberTeamCombatStats(teamId, attack = 10f, hit = 1f, maxHp = 1000f)
      every { progressionService.requirePlayer(playerId) } returnsMany listOf(beforePlayer, afterPlayer)
      every { progressionService.requireZoneProgress(playerId, zoneId) } returnsMany listOf(beforeZone, afterZone)
      every { progressionService.recordKill(playerId, zoneId, any()) } just runs
      every { combatLootService.rollLoot(playerId, zoneId, any()) } returns item
      every { combatStateRepository.save(any()) } answers {
        firstArg<CombatState>().also { savedState = it }
      }
      every { playerEventQueue.enqueue(any()) } just runs
      every { zoneTemplateCatalog.require(zoneId) } returns
        ZoneTemplate(zoneId, "Starter Plains", LevelRange(1, 10), emptyList(), emptyList(), listOf("training-dummy"))
      every { enemyTemplateCatalog.requireRandom(listOf("training-dummy"), randomSource) } returns trainingDummyEnemy(baseHp = 100f)

      val result = service.applyIfNeeded(playerId)
      val actualResult = requireNotNull(result)
      val expectedResult =
        expectedOfflineProgressionResult(
          playerId,
          actualResult.offlineDuration,
          30,
          zoneId,
          listOf(expectedOfflineRewardSummary("sword_0001", 30)),
        ).copy(experienceGained = 320, goldGained = 800)
      val expectedSummary =
        expectedOfflineProgressionMessage(
          playerId,
          actualResult.offlineDuration,
          30,
          zoneId,
          listOf(expectedOfflineRewardSummary("sword_0001", 30)),
        ).copy(experienceGained = 320, goldGained = 800)

      actualResult shouldBe expectedResult
      verify(exactly = 30) { progressionService.recordKill(playerId, zoneId, any()) }
      verify(exactly = 30) { combatLootService.rollLoot(playerId, zoneId, any()) }
      verify(exactly = 1) { playerEventQueue.enqueue(expectedSummary) }
    }

    "applyIfNeeded skips the summary event when the offline duration is below the notify threshold" {
      val playerId = UUID.randomUUID()
      val teamId = UUID.randomUUID()
      val zoneId = "starter-plains"
      val state =
        expectedSingleMemberCombatState(
          playerId = playerId,
          teamId = teamId,
          attack = 10f,
          hit = 1f,
          currentHp = 1000f,
          maxHp = 1000f,
          enemyHp = 100f,
          enemyMaxHp = 100f,
          lastSimulatedAt = Instant.now().minus(Duration.ofMinutes(4)),
        )
      val beforePlayer = expectedPlayer(playerId, "Alice")
      val afterPlayer = expectedPlayer(playerId, "Alice", experience = 210, level = 3, gold = 525)
      val beforeZone = expectedZoneProgress(playerId, zoneId)
      val afterZone = expectedZoneProgress(playerId, zoneId, 21, 3)

      every { combatStateRepository.findById(playerId) } returns state
      every { combatStatService.teamStatsForPlayerOrNull(playerId, state.members) } returns
        expectedSingleMemberTeamCombatStats(teamId, attack = 10f, hit = 1f, maxHp = 1000f)
      every { progressionService.requirePlayer(playerId) } returnsMany listOf(beforePlayer, afterPlayer)
      every { progressionService.requireZoneProgress(playerId, zoneId) } returnsMany listOf(beforeZone, afterZone)
      every { progressionService.recordKill(playerId, zoneId, any()) } just runs
      every { combatLootService.rollLoot(playerId, zoneId, any()) } returns null
      every { combatStateRepository.save(any()) } answers { firstArg() }
      every { playerEventQueue.enqueue(any()) } just runs
      every { zoneTemplateCatalog.require(zoneId) } returns
        ZoneTemplate(zoneId, "Starter Plains", LevelRange(1, 10), emptyList(), emptyList(), listOf("training-dummy"))
      every { enemyTemplateCatalog.requireRandom(listOf("training-dummy"), randomSource) } returns trainingDummyEnemy(baseHp = 100f)

      val result = service.applyIfNeeded(playerId)
      val actualResult = requireNotNull(result)

      actualResult shouldBe
        expectedOfflineProgressionResult(
          playerId,
          actualResult.offlineDuration,
          20,
          zoneId,
          emptyList(),
        ).copy(experienceGained = 210, goldGained = 525)
      verify(exactly = 0) { playerEventQueue.enqueue(any()) }
    }

    "applyIfNeeded matches repeated combat ticks for the same elapsed time" {
      val playerId = UUID.randomUUID()
      val teamId = UUID.randomUUID()
      val zoneId = "starter-plains"
      val offlineDuration = Duration.ofSeconds(37)
      val initialState =
        expectedSingleMemberCombatState(
          playerId = playerId,
          teamId = teamId,
          attack = 10f,
          hit = 1f,
          currentHp = 1000f,
          maxHp = 1000f,
          enemyHp = 100f,
          enemyMaxHp = 100f,
          lastSimulatedAt = Instant.now().minus(offlineDuration),
        )
      val teamStats =
        expectedSingleMemberTeamCombatStats(teamId, attack = 10f, hit = 1f, maxHp = 1000f)

      val offlineCombatStateRepository = mockk<CombatStateRepository>()
      val offlineCombatStatService = mockk<CombatStatService>()
      val offlineProgressionServicePort = mockk<ProgressionService>()
      val offlineCombatLootService = mockk<CombatLootService>()
      val offlinePlayerMessageQueue = mockk<PlayerMessageQueue>()
      val offlineZoneTemplateCatalog = mockk<ZoneTemplateCatalog>()
      val offlineEnemyTemplateCatalog = mockk<EnemyTemplateCatalog>()
      val offlineRandomSource = mockk<RandomSource>()
      val offlineService =
        OfflineProgressionService(
          offlineCombatStateRepository,
          offlineCombatStatService,
          offlineProgressionServicePort,
          offlineCombatLootService,
          offlinePlayerMessageQueue,
          offlineZoneTemplateCatalog,
          offlineEnemyTemplateCatalog,
          offlineRandomSource,
          ScalingRule(config),
          config,
        )

      val liveActivePlayerRegistry = mockk<ActivePlayerRegistry>()
      val liveCombatStateRepository = mockk<CombatStateRepository>()
      val liveCombatStatService = mockk<CombatStatService>()
      val livePlayerStateWorkQueue = mockk<PlayerStateWorkQueue>()
      val liveCombatLootService = mockk<CombatLootService>()
      val liveProgressionService = mockk<ProgressionService>()
      val liveZoneTemplateCatalog = mockk<ZoneTemplateCatalog>()
      val liveEnemyTemplateCatalog = mockk<EnemyTemplateCatalog>()
      val liveRandomSource = mockk<RandomSource>()
      val liveTickService =
        CombatTickService(
          liveActivePlayerRegistry,
          liveCombatStateRepository,
          liveCombatStatService,
          livePlayerStateWorkQueue,
          liveCombatLootService,
          liveProgressionService,
          liveZoneTemplateCatalog,
          liveEnemyTemplateCatalog,
          liveRandomSource,
          ScalingRule(config),
          config,
        )
      val scalingRule = ScalingRule(config)

      var offlineSavedState = initialState
      var offlineKills = 0
      var offlineZoneProgressPoints = 0
      var liveCurrentState = initialState.copy(lastSimulatedAt = null)
      var liveKills = 0
      var liveZoneProgressPoints = 0

      every { offlineCombatStateRepository.findById(playerId) } answers { offlineSavedState }
      every { offlineCombatStateRepository.save(any()) } answers {
        firstArg<CombatState>().also { offlineSavedState = it }
      }
      every { offlineCombatStatService.teamStatsForPlayerOrNull(playerId, any()) } returns teamStats
      every { offlineProgressionServicePort.requirePlayer(playerId) } answers {
        Player(
          playerId,
          "Alice",
          experience = offlineKills * 10,
          level = 1 + (offlineKills / 10),
          gold = offlineKills * 25,
        )
      }
      every { offlineProgressionServicePort.requireZoneProgress(playerId, zoneId) } answers {
        PlayerZoneProgress(
          playerId,
          zoneId,
          killCount = offlineZoneProgressPoints,
          level = scalingRule.zoneLevelForKillCount(offlineZoneProgressPoints),
        )
      }
      every { offlineProgressionServicePort.recordKill(playerId, zoneId, any()) } answers {
        offlineKills += 1
        val zoneLevel = scalingRule.zoneLevelForKillCount(offlineZoneProgressPoints)
        val zoneProgressGain =
          (config.progression().zone().progressMultiplier() * scalingRule.rewardMultiplier(zoneLevel))
            .roundToInt()
            .coerceAtLeast(1)
        offlineZoneProgressPoints += zoneProgressGain
      }
      every { offlineCombatLootService.rollLoot(playerId, zoneId, any()) } returns null
      every { offlinePlayerMessageQueue.enqueue(any()) } just runs
      every { offlineZoneTemplateCatalog.require(zoneId) } returns
        ZoneTemplate(zoneId, "Starter Plains", LevelRange(1, 10), emptyList(), emptyList(), listOf("training-dummy"))
      every {
        offlineEnemyTemplateCatalog.requireRandom(
          listOf("training-dummy"),
          offlineRandomSource,
        )
      } returns trainingDummyEnemy(baseHp = 100f)

      every { liveActivePlayerRegistry.activePlayerIds() } returns setOf(playerId)
      every { liveCombatStateRepository.findById(playerId) } answers { liveCurrentState }
      every { liveCombatStateRepository.findActiveByZoneId(zoneId) } answers { listOf(liveCurrentState) }
      every { liveCombatStateRepository.save(any()) } answers {
        firstArg<CombatState>().also { liveCurrentState = it }
      }
      every { liveCombatStatService.teamStatsForPlayerOrNull(playerId, any()) } returns teamStats
      every { livePlayerStateWorkQueue.markDirty(playerId) } just runs
      every { liveProgressionService.requireZoneProgress(playerId, zoneId) } answers {
        PlayerZoneProgress(
          playerId,
          zoneId,
          killCount = liveZoneProgressPoints,
          level = scalingRule.zoneLevelForKillCount(liveZoneProgressPoints),
        )
      }
      every { liveProgressionService.recordKill(playerId, zoneId, any()) } answers {
        liveKills += 1
        val zoneLevel = scalingRule.zoneLevelForKillCount(liveZoneProgressPoints)
        val zoneProgressGain =
          (config.progression().zone().progressMultiplier() * scalingRule.rewardMultiplier(zoneLevel))
            .roundToInt()
            .coerceAtLeast(1)
        liveZoneProgressPoints += zoneProgressGain
      }
      every { liveCombatLootService.rollLoot(playerId, zoneId, any()) } returns null
      every { liveZoneTemplateCatalog.require(zoneId) } returns
        ZoneTemplate(zoneId, "Starter Plains", LevelRange(1, 10), emptyList(), emptyList(), listOf("training-dummy"))
      every { liveEnemyTemplateCatalog.requireRandom(listOf("training-dummy"), liveRandomSource) } returns trainingDummyEnemy(baseHp = 100f)

      val offlineResult = requireNotNull(offlineService.applyIfNeeded(playerId))
      val actualOfflineDurationMillis = offlineResult.offlineDuration.toMillis()
      repeat((actualOfflineDurationMillis / 200L).toInt()) {
        runBlocking {
          liveTickService.tickZone(zoneId, Duration.ofMillis(200))
        }
      }
      val remainderMillis = actualOfflineDurationMillis % 200L
      if (remainderMillis > 0L) {
        runBlocking {
          liveTickService.tickZone(zoneId, Duration.ofMillis(remainderMillis))
        }
      }

      offlineResult.kills shouldBe liveKills
      offlineResult.zoneId shouldBe zoneId
      offlineResult.rewards shouldBe emptyList()
      offlineResult.experienceGained shouldBe liveKills * 10
      offlineResult.goldGained shouldBe liveKills * 25
      assertCombatStateMatchesWithHpTolerance(
        actual = liveCurrentState.copy(lastSimulatedAt = null),
        expected = offlineSavedState.copy(lastSimulatedAt = null),
      )
    }

    "applyIfNeeded respawns a random enemy from the zone pool after a win" {
      val playerId = UUID.randomUUID()
      val teamId = UUID.randomUUID()
      val zoneId = "starter-plains"
      val state =
        expectedSingleMemberCombatState(
          playerId = playerId,
          teamId = teamId,
          attack = 150f,
          hit = 1f,
          currentHp = 100f,
          maxHp = 100f,
          enemyHp = 0f,
          enemyMaxHp = 100f,
          enemyAttack = 1f,
          status = com.wafuri.idle.domain.model.CombatStatus.WON,
          lastSimulatedAt = Instant.now().minus(Duration.ofSeconds(1)),
        )
      var savedState: CombatState? = null

      every { combatStateRepository.findById(playerId) } returns state
      every { combatStatService.teamStatsForPlayerOrNull(playerId, state.members) } returns
        expectedSingleMemberTeamCombatStats(teamId, attack = 150f, hit = 1f, maxHp = 100f)
      every { progressionService.requirePlayer(playerId) } returnsMany
        listOf(expectedPlayer(playerId, "Alice"), expectedPlayer(playerId, "Alice"))
      every { progressionService.requireZoneProgress(playerId, zoneId) } returnsMany
        listOf(expectedZoneProgress(playerId, zoneId), expectedZoneProgress(playerId, zoneId))
      every { combatStateRepository.save(any()) } answers {
        firstArg<CombatState>().also { savedState = it }
      }
      every { combatLootService.rollLoot(playerId, zoneId, any()) } returns null
      every { playerEventQueue.enqueue(any()) } just runs
      every { zoneTemplateCatalog.require(zoneId) } returns
        ZoneTemplate(zoneId, "Starter Plains", LevelRange(1, 10), emptyList(), emptyList(), listOf("training-dummy", "straw-golem"))
      every { enemyTemplateCatalog.requireRandom(listOf("training-dummy", "straw-golem"), randomSource) } returns strawGolemEnemy()

      val result = requireNotNull(service.applyIfNeeded(playerId))

      result.kills shouldBe 0
      savedState?.status shouldBe com.wafuri.idle.domain.model.CombatStatus.FIGHTING
      savedState?.enemyId shouldBe "straw-golem"
      savedState?.enemyName shouldBe "Straw Golem"
      savedState?.enemyAttack shouldBe 2f
      savedState?.enemyHp shouldBe 1200f
      savedState?.enemyMaxHp shouldBe 1200f
    }
  }
}

private fun assertCombatStateMatchesWithHpTolerance(
  actual: CombatState,
  expected: CombatState,
  hpTolerance: Float = 0.001f,
) {
  actual.playerId shouldBe expected.playerId
  actual.status shouldBe expected.status
  actual.zoneId shouldBe expected.zoneId
  actual.activeTeamId shouldBe expected.activeTeamId
  actual.enemyId shouldBe expected.enemyId
  actual.enemyName shouldBe expected.enemyName
  actual.enemyImage shouldBe expected.enemyImage
  actual.enemyLevel shouldBe expected.enemyLevel
  actual.enemyBaseHp shouldBe expected.enemyBaseHp
  actual.enemyAttack shouldBe expected.enemyAttack
  actual.enemyHp shouldBe expected.enemyHp
  actual.enemyMaxHp shouldBe expected.enemyMaxHp
  actual.pendingDamageMillis shouldBe expected.pendingDamageMillis
  actual.pendingRespawnMillis shouldBe expected.pendingRespawnMillis
  actual.pendingReviveMillis shouldBe expected.pendingReviveMillis
  actual.members.size shouldBe expected.members.size
  actual.members.zip(expected.members).forEach { (actualMember, expectedMember) ->
    actualMember.copy(currentHp = 0f) shouldBe expectedMember.copy(currentHp = 0f)
    (abs(actualMember.currentHp - expectedMember.currentHp) < hpTolerance) shouldBe true
  }
}
