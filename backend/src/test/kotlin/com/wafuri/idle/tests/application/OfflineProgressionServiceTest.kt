package com.wafuri.idle.tests.application

import com.wafuri.idle.application.config.GameConfig
import com.wafuri.idle.application.model.CharacterCombatStats
import com.wafuri.idle.application.model.OfflineProgressionMessage
import com.wafuri.idle.application.model.OfflineRewardSummary
import com.wafuri.idle.application.model.TeamCombatStats
import com.wafuri.idle.application.port.out.ActivePlayerRegistry
import com.wafuri.idle.application.port.out.CombatStateRepository
import com.wafuri.idle.application.port.out.PlayerMessageQueue
import com.wafuri.idle.application.port.out.PlayerStateWorkQueue
import com.wafuri.idle.application.service.combat.CombatLootService
import com.wafuri.idle.application.service.combat.CombatStatService
import com.wafuri.idle.application.service.combat.CombatTickService
import com.wafuri.idle.application.service.player.OfflineProgressionService
import com.wafuri.idle.application.service.player.ProgressionService
import com.wafuri.idle.domain.model.CombatMemberState
import com.wafuri.idle.domain.model.CombatState
import com.wafuri.idle.domain.model.CombatStatus
import com.wafuri.idle.domain.model.InventoryItem
import com.wafuri.idle.domain.model.Player
import com.wafuri.idle.domain.model.PlayerZoneProgress
import com.wafuri.idle.tests.support.gameConfig
import com.wafuri.idle.tests.support.swordItem
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
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
          config,
        )
    }

    "applyIfNeeded initializes last simulated time when combat has never been simulated" {
      val playerId = UUID.randomUUID()
      val teamId = UUID.randomUUID()
      val state =
        CombatState(
          playerId = playerId,
          status = CombatStatus.FIGHTING,
          zoneId = "starter-plains",
          activeTeamId = teamId,
          enemyName = "Training Dummy",
          enemyHp = 100f,
          enemyMaxHp = 100f,
          members = listOf(CombatMemberState("warrior", 10f, 1f, 10f, 10f)),
          lastSimulatedAt = null,
        )

      every { combatStateRepository.findById(playerId) } returns state
      every { combatStateRepository.save(any()) } answers { firstArg() }

      val result = service.applyIfNeeded(playerId)

      result shouldBe null
      verify(exactly = 1) { combatStateRepository.save(match { it.lastSimulatedAt != null }) }
      verify(exactly = 0) { progressionService.recordKill(any(), any()) }
      verify(exactly = 0) { combatLootService.rollLoot(any(), any()) }
      verify(exactly = 0) { playerEventQueue.enqueue(any()) }
    }

    "applyIfNeeded replays offline combat 1 to 1 and emits a summary after the notify threshold" {
      val playerId = UUID.randomUUID()
      val teamId = UUID.randomUUID()
      val zoneId = "starter-plains"
      val state =
        CombatState(
          playerId = playerId,
          status = CombatStatus.FIGHTING,
          zoneId = zoneId,
          activeTeamId = teamId,
          enemyName = "Training Dummy",
          enemyHp = 100f,
          enemyMaxHp = 100f,
          members = listOf(CombatMemberState("warrior", 10f, 1f, 10f, 10f)),
          lastSimulatedAt = Instant.now().minus(Duration.ofMinutes(6)),
        )
      val beforePlayer = Player(id = playerId, name = "Alice", experience = 0, level = 1, gold = 0)
      val afterPlayer = Player(id = playerId, name = "Alice", experience = 320, level = 4, gold = 800)
      val beforeZone = PlayerZoneProgress(playerId = playerId, zoneId = zoneId, killCount = 0, level = 1)
      val afterZone = PlayerZoneProgress(playerId = playerId, zoneId = zoneId, killCount = 32, level = 4)
      val item =
        InventoryItem(
          id = UUID.randomUUID(),
          playerId = playerId,
          item = swordItem(),
        )
      var savedState: CombatState? = null

      every { combatStateRepository.findById(playerId) } returns state
      every { combatStatService.teamStatsForPlayer(playerId, state.members) } returns
        TeamCombatStats(
          teamId = teamId,
          characterStats = listOf(CharacterCombatStats("warrior", 10f, 1f, 10f)),
        )
      every { progressionService.requirePlayer(playerId) } returnsMany listOf(beforePlayer, afterPlayer)
      every { progressionService.requireZoneProgress(playerId, zoneId) } returnsMany listOf(beforeZone, afterZone)
      every { progressionService.recordKill(playerId, zoneId) } just runs
      every { combatLootService.rollLoot(playerId, zoneId) } returns item
      every { combatStateRepository.save(any()) } answers {
        firstArg<CombatState>().also { savedState = it }
      }
      every { playerEventQueue.enqueue(any()) } just runs

      val result = service.applyIfNeeded(playerId)

      result?.kills shouldBe 32
      result?.experienceGained shouldBe 320
      result?.goldGained shouldBe 800
      result?.playerLevel shouldBe 4
      result?.playerLevelsGained shouldBe 3
      result?.zoneLevel shouldBe 4
      result?.zoneLevelsGained shouldBe 3
      result?.rewards shouldContainExactly listOf(OfflineRewardSummary(itemName = "sword_0001", count = 32))
      savedState?.status shouldBe CombatStatus.FIGHTING
      savedState?.enemyHp shouldBe 20f
      savedState?.lastSimulatedAt?.isAfter(state.lastSimulatedAt) shouldBe true
      verify(exactly = 32) { progressionService.recordKill(playerId, zoneId) }
      verify(exactly = 32) { combatLootService.rollLoot(playerId, zoneId) }
      verify(exactly = 1) {
        playerEventQueue.enqueue(
          match {
            it is OfflineProgressionMessage &&
              it.serverTime == null &&
              it.playerId == playerId &&
              it.kills == 32 &&
              it.experienceGained == 320 &&
              it.goldGained == 800 &&
              it.playerLevel == 4 &&
              it.playerLevelsGained == 3 &&
              it.zoneId == zoneId &&
              it.zoneLevel == 4 &&
              it.zoneLevelsGained == 3 &&
              it.rewards == listOf(OfflineRewardSummary(itemName = "sword_0001", count = 32)) &&
              it.offlineDurationMillis >= Duration.ofMinutes(5).toMillis()
          },
        )
      }
    }

    "applyIfNeeded skips the summary event when the offline duration is below the notify threshold" {
      val playerId = UUID.randomUUID()
      val teamId = UUID.randomUUID()
      val zoneId = "starter-plains"
      val state =
        CombatState(
          playerId = playerId,
          status = CombatStatus.FIGHTING,
          zoneId = zoneId,
          activeTeamId = teamId,
          enemyName = "Training Dummy",
          enemyHp = 100f,
          enemyMaxHp = 100f,
          members = listOf(CombatMemberState("warrior", 10f, 1f, 10f, 10f)),
          lastSimulatedAt = Instant.now().minus(Duration.ofMinutes(4)),
        )
      val beforePlayer = Player(id = playerId, name = "Alice", experience = 0, level = 1, gold = 0)
      val afterPlayer = Player(id = playerId, name = "Alice", experience = 210, level = 3, gold = 525)
      val beforeZone = PlayerZoneProgress(playerId = playerId, zoneId = zoneId, killCount = 0, level = 1)
      val afterZone = PlayerZoneProgress(playerId = playerId, zoneId = zoneId, killCount = 21, level = 3)

      every { combatStateRepository.findById(playerId) } returns state
      every { combatStatService.teamStatsForPlayer(playerId, state.members) } returns
        TeamCombatStats(
          teamId = teamId,
          characterStats = listOf(CharacterCombatStats("warrior", 10f, 1f, 10f)),
        )
      every { progressionService.requirePlayer(playerId) } returnsMany listOf(beforePlayer, afterPlayer)
      every { progressionService.requireZoneProgress(playerId, zoneId) } returnsMany listOf(beforeZone, afterZone)
      every { progressionService.recordKill(playerId, zoneId) } just runs
      every { combatLootService.rollLoot(playerId, zoneId) } returns null
      every { combatStateRepository.save(any()) } answers { firstArg() }
      every { playerEventQueue.enqueue(any()) } just runs

      val result = service.applyIfNeeded(playerId)

      result?.kills shouldBe 21
      result?.experienceGained shouldBe 210
      result?.goldGained shouldBe 525
      result?.rewards shouldBe emptyList()
      verify(exactly = 0) { playerEventQueue.enqueue(any()) }
    }

    "applyIfNeeded matches repeated combat ticks for the same elapsed time" {
      val playerId = UUID.randomUUID()
      val teamId = UUID.randomUUID()
      val zoneId = "starter-plains"
      val offlineDuration = Duration.ofSeconds(37)
      val initialState =
        CombatState(
          playerId = playerId,
          status = CombatStatus.FIGHTING,
          zoneId = zoneId,
          activeTeamId = teamId,
          enemyName = "Training Dummy",
          enemyHp = 100f,
          enemyMaxHp = 100f,
          members = listOf(CombatMemberState("warrior", 10f, 1f, 10f, 10f)),
          lastSimulatedAt = Instant.now().minus(offlineDuration),
        )
      val teamStats =
        TeamCombatStats(
          teamId = teamId,
          characterStats = listOf(CharacterCombatStats("warrior", 10f, 1f, 10f)),
        )

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
      every { offlineProgressionServicePort.recordKill(playerId, zoneId) } answers { offlineKills += 1 }
      every { offlineCombatLootService.rollLoot(playerId, zoneId) } returns null
      every { offlinePlayerMessageQueue.enqueue(any()) } just runs

      every { liveActivePlayerRegistry.activePlayerIds() } returns setOf(playerId)
      every { liveCombatStateRepository.findById(playerId) } answers { liveCurrentState }
      every { liveCombatStateRepository.findActiveByZoneId(zoneId) } answers { listOf(liveCurrentState) }
      every { liveCombatStateRepository.save(any()) } answers {
        firstArg<CombatState>().also { liveCurrentState = it }
      }
      every { liveCombatStatService.teamStatsForPlayer(playerId, any()) } returns teamStats
      every { livePlayerStateWorkQueue.markDirty(playerId) } just runs
      every { liveProgressionService.recordKill(playerId, zoneId) } answers { liveKills += 1 }
      every { liveCombatLootService.rollLoot(playerId, zoneId) } returns null

      val offlineResult = offlineService.applyIfNeeded(playerId)
      val actualOfflineDurationMillis = requireNotNull(offlineResult).offlineDuration.toMillis()
      repeat((actualOfflineDurationMillis / 200L).toInt()) {
        liveTickService.tickPlayer(playerId, Duration.ofMillis(200))
      }
      val remainderMillis = actualOfflineDurationMillis % 200L
      if (remainderMillis > 0L) {
        liveTickService.tickPlayer(playerId, Duration.ofMillis(remainderMillis))
      }

      offlineResult.kills shouldBe liveKills
      offlineResult.experienceGained shouldBe liveKills * 10
      offlineResult.goldGained shouldBe liveKills * 25
      offlineResult.zoneLevelsGained shouldBe liveKills / 10
      offlineResult.rewards shouldBe emptyList()
      offlineSavedState.copy(lastSimulatedAt = null) shouldBe liveCurrentState.copy(lastSimulatedAt = null)
    }
  }
}
