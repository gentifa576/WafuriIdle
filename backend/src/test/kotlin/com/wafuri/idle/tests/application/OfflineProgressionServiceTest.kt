package com.wafuri.idle.tests.application

import com.wafuri.idle.application.config.GameConfig
import com.wafuri.idle.application.port.out.ActivePlayerRegistry
import com.wafuri.idle.application.port.out.CombatStateRepository
import com.wafuri.idle.application.port.out.PlayerMessageQueue
import com.wafuri.idle.application.port.out.PlayerStateWorkQueue
import com.wafuri.idle.application.service.combat.CombatLootService
import com.wafuri.idle.application.service.combat.CombatStatService
import com.wafuri.idle.application.service.combat.CombatTickService
import com.wafuri.idle.application.service.player.OfflineProgressionService
import com.wafuri.idle.application.service.player.ProgressionService
import com.wafuri.idle.application.service.scaling.ScalingRule
import com.wafuri.idle.domain.model.CombatState
import com.wafuri.idle.domain.model.Player
import com.wafuri.idle.domain.model.PlayerZoneProgress
import com.wafuri.idle.tests.support.expectedInventoryItem
import com.wafuri.idle.tests.support.expectedOfflineProgressionMessage
import com.wafuri.idle.tests.support.expectedOfflineProgressionResult
import com.wafuri.idle.tests.support.expectedOfflineRewardSummary
import com.wafuri.idle.tests.support.expectedPlayer
import com.wafuri.idle.tests.support.expectedSingleMemberCombatState
import com.wafuri.idle.tests.support.expectedSingleMemberTeamCombatStats
import com.wafuri.idle.tests.support.expectedZoneProgress
import com.wafuri.idle.tests.support.gameConfig
import com.wafuri.idle.tests.support.swordItem
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import java.time.Duration
import java.time.Instant
import java.util.UUID

class OfflineProgressionServiceTest : StringSpec() {
  private lateinit var combatStateRepository: CombatStateRepository
  private lateinit var combatStatService: CombatStatService
  private lateinit var progressionService: ProgressionService
  private lateinit var combatLootService: CombatLootService
  private lateinit var playerEventQueue: PlayerMessageQueue
  private lateinit var config: GameConfig
  private lateinit var service: OfflineProgressionService

  init {
    beforeTest {
      combatStateRepository = mockk()
      combatStatService = mockk()
      progressionService = mockk()
      combatLootService = mockk()
      playerEventQueue = mockk()
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

      val result = service.applyIfNeeded(playerId)

      result shouldBe null
      verify(exactly = 1) { combatStateRepository.save(match { it.lastSimulatedAt != null }) }
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
      val beforePlayer = expectedPlayer(id = playerId, name = "Alice")
      val afterPlayer = expectedPlayer(id = playerId, name = "Alice", experience = 320, level = 4, gold = 800)
      val beforeZone = expectedZoneProgress(playerId = playerId, zoneId = zoneId)
      val afterZone = expectedZoneProgress(playerId = playerId, zoneId = zoneId, killCount = 32, level = 4)
      val item =
        expectedInventoryItem(
          id = UUID.randomUUID(),
          playerId = playerId,
          item = swordItem(),
        )
      var savedState: CombatState? = null

      every { combatStateRepository.findById(playerId) } returns state
      every { combatStatService.teamStatsForPlayer(playerId, state.members) } returns
        expectedSingleMemberTeamCombatStats(teamId = teamId, attack = 10f, hit = 1f, maxHp = 1000f)
      every { progressionService.requirePlayer(playerId) } returnsMany listOf(beforePlayer, afterPlayer)
      every { progressionService.requireZoneProgress(playerId, zoneId) } returnsMany listOf(beforeZone, afterZone)
      every { progressionService.recordKill(playerId, zoneId, any()) } just runs
      every { combatLootService.rollLoot(playerId, zoneId, any()) } returns item
      every { combatStateRepository.save(any()) } answers {
        firstArg<CombatState>().also { savedState = it }
      }
      every { playerEventQueue.enqueue(any()) } just runs

      val result = service.applyIfNeeded(playerId)
      val actualResult = requireNotNull(result)
      val expectedResult =
        expectedOfflineProgressionResult(
          playerId = playerId,
          offlineDuration = actualResult.offlineDuration,
          kills = 30,
          zoneId = zoneId,
          rewards = listOf(expectedOfflineRewardSummary(itemName = "sword_0001", count = 30)),
        ).copy(experienceGained = 320, goldGained = 800)
      val expectedSummary =
        expectedOfflineProgressionMessage(
          playerId = playerId,
          offlineDuration = actualResult.offlineDuration,
          kills = 30,
          zoneId = zoneId,
          rewards = listOf(expectedOfflineRewardSummary(itemName = "sword_0001", count = 30)),
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
      val beforePlayer = expectedPlayer(id = playerId, name = "Alice")
      val afterPlayer = expectedPlayer(id = playerId, name = "Alice", experience = 210, level = 3, gold = 525)
      val beforeZone = expectedZoneProgress(playerId = playerId, zoneId = zoneId)
      val afterZone = expectedZoneProgress(playerId = playerId, zoneId = zoneId, killCount = 21, level = 3)

      every { combatStateRepository.findById(playerId) } returns state
      every { combatStatService.teamStatsForPlayer(playerId, state.members) } returns
        expectedSingleMemberTeamCombatStats(teamId = teamId, attack = 10f, hit = 1f, maxHp = 1000f)
      every { progressionService.requirePlayer(playerId) } returnsMany listOf(beforePlayer, afterPlayer)
      every { progressionService.requireZoneProgress(playerId, zoneId) } returnsMany listOf(beforeZone, afterZone)
      every { progressionService.recordKill(playerId, zoneId, any()) } just runs
      every { combatLootService.rollLoot(playerId, zoneId, any()) } returns null
      every { combatStateRepository.save(any()) } answers { firstArg() }
      every { playerEventQueue.enqueue(any()) } just runs

      val result = service.applyIfNeeded(playerId)
      val actualResult = requireNotNull(result)

      actualResult shouldBe
        expectedOfflineProgressionResult(
          playerId = playerId,
          offlineDuration = actualResult.offlineDuration,
          kills = 20,
          zoneId = zoneId,
          rewards = emptyList(),
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
        expectedSingleMemberTeamCombatStats(teamId = teamId, attack = 10f, hit = 1f, maxHp = 1000f)

      val offlineCombatStateRepository = mockk<CombatStateRepository>()
      val offlineCombatStatService = mockk<CombatStatService>()
      val offlineProgressionServicePort = mockk<ProgressionService>()
      val offlineCombatLootService = mockk<CombatLootService>()
      val offlinePlayerMessageQueue = mockk<PlayerMessageQueue>()
      val offlineService =
        OfflineProgressionService(
          offlineCombatStateRepository,
          offlineCombatStatService,
          offlineProgressionServicePort,
          offlineCombatLootService,
          offlinePlayerMessageQueue,
          ScalingRule(config),
          config,
        )

      val liveActivePlayerRegistry = mockk<ActivePlayerRegistry>()
      val liveCombatStateRepository = mockk<CombatStateRepository>()
      val liveCombatStatService = mockk<CombatStatService>()
      val livePlayerStateWorkQueue = mockk<PlayerStateWorkQueue>()
      val liveCombatLootService = mockk<CombatLootService>()
      val liveProgressionService = mockk<ProgressionService>()
      val liveTickService =
        CombatTickService(
          liveActivePlayerRegistry,
          liveCombatStateRepository,
          liveCombatStatService,
          livePlayerStateWorkQueue,
          liveCombatLootService,
          liveProgressionService,
          ScalingRule(config),
          config,
        )

      var offlineSavedState = initialState
      var offlineKills = 0
      var liveCurrentState = initialState.copy(lastSimulatedAt = null)
      var liveKills = 0

      every { offlineCombatStateRepository.findById(playerId) } answers { offlineSavedState }
      every { offlineCombatStateRepository.save(any()) } answers {
        firstArg<CombatState>().also { offlineSavedState = it }
      }
      every { offlineCombatStatService.teamStatsForPlayer(playerId, any()) } returns teamStats
      every { offlineProgressionServicePort.requirePlayer(playerId) } answers {
        Player(
          id = playerId,
          name = "Alice",
          experience = offlineKills * 10,
          level = 1 + (offlineKills / 10),
          gold = offlineKills * 25,
        )
      }
      every { offlineProgressionServicePort.requireZoneProgress(playerId, zoneId) } answers {
        PlayerZoneProgress(
          playerId = playerId,
          zoneId = zoneId,
          killCount = offlineKills,
          level = 1 + (offlineKills / 10),
        )
      }
      every { offlineProgressionServicePort.recordKill(playerId, zoneId, any()) } answers { offlineKills += 1 }
      every { offlineCombatLootService.rollLoot(playerId, zoneId, any()) } returns null
      every { offlinePlayerMessageQueue.enqueue(any()) } just runs

      every { liveActivePlayerRegistry.activePlayerIds() } returns setOf(playerId)
      every { liveCombatStateRepository.findById(playerId) } answers { liveCurrentState }
      every { liveCombatStateRepository.findActiveByZoneId(zoneId) } answers { listOf(liveCurrentState) }
      every { liveCombatStateRepository.save(any()) } answers {
        firstArg<CombatState>().also { liveCurrentState = it }
      }
      every { liveCombatStatService.teamStatsForPlayer(playerId, any()) } returns teamStats
      every { livePlayerStateWorkQueue.markDirty(playerId) } just runs
      every { liveProgressionService.requireZoneProgress(playerId, zoneId) } answers {
        PlayerZoneProgress(
          playerId = playerId,
          zoneId = zoneId,
          killCount = liveKills,
          level = 1 + (liveKills / 10),
        )
      }
      every { liveProgressionService.recordKill(playerId, zoneId, any()) } answers { liveKills += 1 }
      every { liveCombatLootService.rollLoot(playerId, zoneId, any()) } returns null

      val offlineResult = requireNotNull(offlineService.applyIfNeeded(playerId))
      val actualOfflineDurationMillis = offlineResult.offlineDuration.toMillis()
      repeat((actualOfflineDurationMillis / 200L).toInt()) {
        liveTickService.tickPlayer(playerId, Duration.ofMillis(200))
      }
      val remainderMillis = actualOfflineDurationMillis % 200L
      if (remainderMillis > 0L) {
        liveTickService.tickPlayer(playerId, Duration.ofMillis(remainderMillis))
      }

      offlineResult shouldBe
        expectedOfflineProgressionResult(
          playerId = playerId,
          offlineDuration = offlineResult.offlineDuration,
          kills = liveKills,
          zoneId = zoneId,
          rewards = emptyList(),
        )
      offlineSavedState.copy(lastSimulatedAt = null) shouldBe liveCurrentState.copy(lastSimulatedAt = null)
    }
  }
}
