package com.wafuri.idle.tests.application

import com.wafuri.idle.application.config.GameConfig
import com.wafuri.idle.application.port.out.ActivePlayerRegistry
import com.wafuri.idle.application.port.out.CombatStateRepository
import com.wafuri.idle.application.port.out.PlayerStateWorkQueue
import com.wafuri.idle.application.service.combat.CombatLootService
import com.wafuri.idle.application.service.combat.CombatStatService
import com.wafuri.idle.application.service.combat.CombatTickService
import com.wafuri.idle.application.service.player.ProgressionService
import com.wafuri.idle.application.service.scaling.ScalingRule
import com.wafuri.idle.domain.model.CombatState
import com.wafuri.idle.domain.model.CombatStatus
import com.wafuri.idle.tests.support.expectedCombatMemberState
import com.wafuri.idle.tests.support.expectedCombatState
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
import kotlinx.coroutines.runBlocking
import java.time.Duration
import java.util.UUID

class CombatTickServiceTest : StringSpec() {
  private lateinit var activePlayerRegistry: ActivePlayerRegistry
  private lateinit var combatStateRepository: CombatStateRepository
  private lateinit var combatStatService: CombatStatService
  private lateinit var playerStateWorkQueue: PlayerStateWorkQueue
  private lateinit var combatLootService: CombatLootService
  private lateinit var progressionService: ProgressionService
  private lateinit var config: GameConfig
  private lateinit var service: CombatTickService

  init {
    beforeTest {
      activePlayerRegistry = mockk()
      combatStateRepository = mockk()
      combatStatService = mockk()
      playerStateWorkQueue = mockk()
      combatLootService = mockk()
      progressionService = mockk()
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
          playerStateWorkQueue,
          combatLootService,
          progressionService,
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
      every { combatStateRepository.save(any()) } answers {
        firstArg<CombatState>().also { savedState = it }
      }
      every { playerStateWorkQueue.markDirty(playerId) } just runs
      every { combatLootService.rollLoot(any(), any(), any()) } returns null
      every { progressionService.recordKill(any(), any(), any()) } returns Unit

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
      every { combatStateRepository.save(any()) } answers {
        firstArg<CombatState>().also { savedState = it }
      }
      every { playerStateWorkQueue.markDirty(playerId) } just runs

      runBlocking {
        service.tickZone("starter-plains", Duration.ofMillis(200))
      }

      savedState shouldBe CombatState.idle(playerId, lastSimulatedAt = savedState?.lastSimulatedAt)
      verify(exactly = 1) { playerStateWorkQueue.markDirty(playerId) }
    }
  }
}
