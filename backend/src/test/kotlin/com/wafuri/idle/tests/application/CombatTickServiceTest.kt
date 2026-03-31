package com.wafuri.idle.tests.application

import com.wafuri.idle.application.config.GameConfig
import com.wafuri.idle.application.model.CharacterCombatStats
import com.wafuri.idle.application.model.TeamCombatStats
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
      every { combatStatService.teamStatsForPlayer(playerId, current.members) } returns
        expectedSingleMemberTeamCombatStats(teamId = teamId, attack = 12f, hit = 7f, maxHp = 11f)
      every { combatStateRepository.save(any()) } answers {
        firstArg<CombatState>().also { savedState = it }
      }
      every { playerStateWorkQueue.markDirty(playerId) } just runs
      every { combatLootService.rollLoot(any(), any(), any()) } returns null
      every { progressionService.recordKill(any(), any(), any()) } returns Unit

      service.tickZone("starter-plains", Duration.ofMillis(200))

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

      service.tickZone("starter-plains", Duration.ofSeconds(1))

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

    "tick self corrects when elapsed time drifts past the damage cadence" {
      val playerId = UUID.randomUUID()
      val teamId = UUID.randomUUID()
      var currentState =
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

      every { combatStateRepository.findActiveByZoneId("starter-plains") } answers { listOf(currentState) }
      every { activePlayerRegistry.activePlayerIds() } returns setOf(playerId)
      every { combatStateRepository.findById(playerId) } answers { currentState }
      every { combatStatService.teamStatsForPlayer(playerId, any()) } returns
        expectedSingleMemberTeamCombatStats(teamId = teamId, attack = 12f, hit = 7f, maxHp = 11f)
      every { combatStateRepository.save(any()) } answers {
        firstArg<CombatState>().also { currentState = it }
      }
      every { playerStateWorkQueue.markDirty(playerId) } just runs
      every { combatLootService.rollLoot(any(), any(), any()) } returns null
      every { progressionService.recordKill(any(), any(), any()) } returns Unit

      service.tickZone("starter-plains", Duration.ofMillis(1200))

      currentState shouldBe
        expectedCombatState(
          playerId = playerId,
          status = CombatStatus.FIGHTING,
          zoneId = "starter-plains",
          activeTeamId = teamId,
          enemyName = "Training Dummy",
          enemyHp = 916f,
          enemyMaxHp = 1000f,
          members = listOf(expectedCombatMemberState("warrior", 12f, 7f, 10f, 11f)),
          pendingDamageMillis = 200L,
          lastSimulatedAt = currentState.lastSimulatedAt,
        )

      service.tickZone("starter-plains", Duration.ofMillis(800))

      currentState shouldBe
        expectedCombatState(
          playerId = playerId,
          status = CombatStatus.FIGHTING,
          zoneId = "starter-plains",
          activeTeamId = teamId,
          enemyName = "Training Dummy",
          enemyHp = 832f,
          enemyMaxHp = 1000f,
          members = listOf(expectedCombatMemberState("warrior", 12f, 7f, 9f, 11f)),
          lastSimulatedAt = currentState.lastSimulatedAt,
        )
      verify(exactly = 2) { playerStateWorkQueue.markDirty(playerId) }
    }

    "tick respawns the enemy after the configured delay and resumes combat" {
      val playerId = UUID.randomUUID()
      val teamId = UUID.randomUUID()
      var currentState =
        expectedSingleMemberCombatState(
          playerId = playerId,
          teamId = teamId,
          attack = 12f,
          hit = 7f,
          currentHp = 11f,
          maxHp = 11f,
          status = CombatStatus.WON,
          enemyHp = 0f,
          enemyMaxHp = 1000f,
        )

      every { combatStateRepository.findActiveByZoneId("starter-plains") } answers { listOf(currentState) }
      every { activePlayerRegistry.activePlayerIds() } returns setOf(playerId)
      every { combatStateRepository.findById(playerId) } answers { currentState }
      every { combatStatService.teamStatsForPlayer(playerId, currentState.members) } returns
        expectedSingleMemberTeamCombatStats(teamId = teamId, attack = 12f, hit = 7f, maxHp = 11f)
      every { combatStateRepository.save(any()) } answers {
        firstArg<CombatState>().also { currentState = it }
      }
      every { playerStateWorkQueue.markDirty(playerId) } just runs
      every { combatLootService.rollLoot(any(), any(), any()) } returns null
      every { progressionService.requireZoneProgress(playerId, "starter-plains") } returns mockk { every { level } returns 1 }
      every { progressionService.recordKill(any(), any(), any()) } returns Unit

      service.tickZone("starter-plains", Duration.ofMillis(500))

      currentState shouldBe
        expectedCombatState(
          playerId = playerId,
          status = CombatStatus.WON,
          zoneId = "starter-plains",
          activeTeamId = teamId,
          enemyName = "Training Dummy",
          enemyHp = 0f,
          enemyMaxHp = 1000f,
          members = listOf(expectedCombatMemberState("warrior", 12f, 7f, 11f, 11f)),
          pendingRespawnMillis = 500L,
          lastSimulatedAt = currentState.lastSimulatedAt,
        )
      verify(exactly = 0) { playerStateWorkQueue.markDirty(playerId) }

      service.tickZone("starter-plains", Duration.ofMillis(700))

      currentState shouldBe
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
          lastSimulatedAt = currentState.lastSimulatedAt,
        )
      verify(exactly = 1) { playerStateWorkQueue.markDirty(playerId) }
      verify(exactly = 0) { progressionService.recordKill(playerId, "starter-plains", any()) }
      verify(exactly = 0) { combatLootService.rollLoot(playerId, "starter-plains", any()) }
    }

    "tick awards kill progression and loot when combat is first won" {
      val playerId = UUID.randomUUID()
      val teamId = UUID.randomUUID()
      var currentState =
        expectedSingleMemberCombatState(
          playerId = playerId,
          teamId = teamId,
          attack = 12f,
          hit = 7f,
          currentHp = 11f,
          maxHp = 11f,
          enemyHp = 10f,
          enemyMaxHp = 10f,
        )

      every { combatStateRepository.findActiveByZoneId("starter-plains") } answers { listOf(currentState) }
      every { activePlayerRegistry.activePlayerIds() } returns setOf(playerId)
      every { combatStateRepository.findById(playerId) } answers { currentState }
      every { combatStatService.teamStatsForPlayer(playerId, currentState.members) } returns
        expectedSingleMemberTeamCombatStats(teamId = teamId, attack = 12f, hit = 7f, maxHp = 11f)
      every { combatStateRepository.save(any()) } answers {
        firstArg<CombatState>().also { currentState = it }
      }
      every { playerStateWorkQueue.markDirty(playerId) } just runs
      every { combatLootService.rollLoot(playerId, "starter-plains", 1) } returns null
      every { progressionService.recordKill(playerId, "starter-plains", 1) } returns Unit

      service.tickZone("starter-plains", Duration.ofSeconds(1))

      currentState shouldBe
        expectedCombatState(
          playerId = playerId,
          status = CombatStatus.WON,
          zoneId = "starter-plains",
          activeTeamId = teamId,
          enemyName = "Training Dummy",
          enemyHp = 0f,
          enemyMaxHp = 10f,
          members = listOf(expectedCombatMemberState("warrior", 12f, 7f, 10f, 11f)),
          lastSimulatedAt = currentState.lastSimulatedAt,
        )
      verify(exactly = 1) { progressionService.recordKill(playerId, "starter-plains", 1) }
      verify(exactly = 1) { combatLootService.rollLoot(playerId, "starter-plains", 1) }
    }

    "tick leaves a wiped team down and revives it at half hp after the revive delay" {
      val playerId = UUID.randomUUID()
      val teamId = UUID.randomUUID()
      var currentState =
        expectedSingleMemberCombatState(
          playerId = playerId,
          teamId = teamId,
          attack = 1f,
          hit = 1f,
          currentHp = 1f,
          maxHp = 10f,
          enemyHp = 1000f,
          enemyMaxHp = 1000f,
        )

      every { combatStateRepository.findActiveByZoneId("starter-plains") } answers { listOf(currentState) }
      every { activePlayerRegistry.activePlayerIds() } returns setOf(playerId)
      every { combatStateRepository.findById(playerId) } answers { currentState }
      every { combatStatService.teamStatsForPlayer(playerId, any()) } returns
        expectedSingleMemberTeamCombatStats(teamId = teamId, characterKey = "warrior", attack = 1f, hit = 1f, maxHp = 10f)
      every { combatStateRepository.save(any()) } answers {
        firstArg<CombatState>().also { currentState = it }
      }
      every { playerStateWorkQueue.markDirty(playerId) } just runs
      every { combatLootService.rollLoot(any(), any(), any()) } returns null
      every { progressionService.recordKill(any(), any(), any()) } returns Unit
      every { progressionService.requireZoneProgress(playerId, "starter-plains") } returns mockk { every { level } returns 1 }

      service.tickZone("starter-plains", Duration.ofSeconds(1))

      currentState shouldBe
        expectedCombatState(
          playerId = playerId,
          status = CombatStatus.DOWN,
          zoneId = "starter-plains",
          activeTeamId = teamId,
          enemyName = "Training Dummy",
          enemyHp = 999f,
          enemyMaxHp = 1000f,
          members = listOf(expectedCombatMemberState("warrior", 1f, 1f, 0f, 10f)),
          lastSimulatedAt = currentState.lastSimulatedAt,
        )

      service.tickZone("starter-plains", Duration.ofSeconds(30))

      currentState shouldBe
        expectedCombatState(
          playerId = playerId,
          status = CombatStatus.FIGHTING,
          zoneId = "starter-plains",
          activeTeamId = teamId,
          enemyName = "Training Dummy",
          enemyHp = 1000f,
          enemyMaxHp = 1000f,
          members = listOf(expectedCombatMemberState("warrior", 1f, 1f, 5f, 10f)),
          lastSimulatedAt = currentState.lastSimulatedAt,
        )
    }

    "tick refreshes combat members when team composition changes" {
      val playerId = UUID.randomUUID()
      val teamId = UUID.randomUUID()
      var currentState =
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

      every { combatStateRepository.findActiveByZoneId("starter-plains") } answers { listOf(currentState) }
      every { activePlayerRegistry.activePlayerIds() } returns setOf(playerId)
      every { combatStateRepository.findById(playerId) } answers { currentState }
      every { combatStatService.teamStatsForPlayer(playerId, currentState.members) } returns
        TeamCombatStats(
          teamId,
          listOf(
            CharacterCombatStats("warrior", 12f, 7f, 11f),
            CharacterCombatStats("cleric", 5f, 4f, 8f),
          ),
        )
      every { combatStateRepository.save(any()) } answers {
        firstArg<CombatState>().also { currentState = it }
      }
      every { playerStateWorkQueue.markDirty(playerId) } just runs
      every { combatLootService.rollLoot(any(), any(), any()) } returns null
      every { progressionService.recordKill(any(), any(), any()) } returns Unit

      service.tickZone("starter-plains", Duration.ZERO)

      currentState shouldBe
        expectedCombatState(
          playerId = playerId,
          status = CombatStatus.FIGHTING,
          zoneId = "starter-plains",
          activeTeamId = teamId,
          enemyName = "Training Dummy",
          enemyHp = 1000f,
          enemyMaxHp = 1000f,
          members =
            listOf(
              expectedCombatMemberState("warrior", 12f, 7f, 11f, 11f),
              expectedCombatMemberState("cleric", 5f, 4f, 8f, 8f),
            ),
          lastSimulatedAt = currentState.lastSimulatedAt,
        )
      verify(exactly = 1) { playerStateWorkQueue.markDirty(playerId) }
    }

    "tick refreshes active team id when player switches active teams during combat" {
      val playerId = UUID.randomUUID()
      val originalTeamId = UUID.randomUUID()
      val newTeamId = UUID.randomUUID()
      var currentState =
        expectedSingleMemberCombatState(
          playerId = playerId,
          teamId = originalTeamId,
          attack = 12f,
          hit = 7f,
          currentHp = 11f,
          maxHp = 11f,
          enemyHp = 1000f,
          enemyMaxHp = 1000f,
        )

      every { combatStateRepository.findActiveByZoneId("starter-plains") } answers { listOf(currentState) }
      every { activePlayerRegistry.activePlayerIds() } returns setOf(playerId)
      every { combatStateRepository.findById(playerId) } answers { currentState }
      every { combatStatService.teamStatsForPlayer(playerId, currentState.members) } returns
        TeamCombatStats(
          newTeamId,
          listOf(CharacterCombatStats("cleric", 5f, 4f, 8f)),
        )
      every { combatStateRepository.save(any()) } answers {
        firstArg<CombatState>().also { currentState = it }
      }
      every { playerStateWorkQueue.markDirty(playerId) } just runs
      every { combatLootService.rollLoot(any(), any(), any()) } returns null
      every { progressionService.recordKill(any(), any(), any()) } returns Unit

      service.tickZone("starter-plains", Duration.ZERO)

      currentState shouldBe
        expectedCombatState(
          playerId = playerId,
          status = CombatStatus.FIGHTING,
          zoneId = "starter-plains",
          activeTeamId = newTeamId,
          enemyName = "Training Dummy",
          enemyHp = 1000f,
          enemyMaxHp = 1000f,
          members = listOf(expectedCombatMemberState("cleric", 5f, 4f, 8f, 8f)),
          lastSimulatedAt = currentState.lastSimulatedAt,
        )
      verify(exactly = 1) { playerStateWorkQueue.markDirty(playerId) }
    }

    "tick processes all active zones" {
      val zoneA = "starter-plains"
      val zoneB = "deep-woods"
      val playerA = UUID.randomUUID()
      val playerB = UUID.randomUUID()
      val teamA = UUID.randomUUID()
      val teamB = UUID.randomUUID()
      var stateA =
        expectedSingleMemberCombatState(
          playerId = playerA,
          teamId = teamA,
          attack = 10f,
          hit = 5f,
          currentHp = 10f,
          maxHp = 10f,
          zoneId = zoneA,
          enemyHp = 1000f,
          enemyMaxHp = 1000f,
        )
      var stateB =
        expectedSingleMemberCombatState(
          playerId = playerB,
          teamId = teamB,
          attack = 8f,
          hit = 5f,
          currentHp = 10f,
          maxHp = 10f,
          zoneId = zoneB,
          enemyName = "Forest Slime",
          enemyHp = 1000f,
          enemyMaxHp = 1000f,
        )

      every { combatStateRepository.findActiveByZoneId(zoneA) } answers { listOf(stateA) }
      every { combatStateRepository.findActiveByZoneId(zoneB) } answers { listOf(stateB) }
      every { activePlayerRegistry.activePlayerIds() } returns setOf(playerA, playerB)
      every { combatStateRepository.findById(playerA) } answers { stateA }
      every { combatStateRepository.findById(playerB) } answers { stateB }
      every { combatStatService.teamStatsForPlayer(playerA, stateA.members) } returns
        expectedSingleMemberTeamCombatStats(teamId = teamA, attack = 10f, hit = 5f, maxHp = 10f)
      every { combatStatService.teamStatsForPlayer(playerB, stateB.members) } returns
        expectedSingleMemberTeamCombatStats(teamId = teamB, attack = 8f, hit = 5f, maxHp = 10f)
      every { combatStateRepository.save(match { it.playerId == playerA }) } answers {
        firstArg<CombatState>().also { stateA = it }
      }
      every { combatStateRepository.save(match { it.playerId == playerB }) } answers {
        firstArg<CombatState>().also { stateB = it }
      }
      every { playerStateWorkQueue.markDirty(any()) } just runs
      every { combatLootService.rollLoot(any(), any(), any()) } returns null
      every { progressionService.recordKill(any(), any(), any()) } returns Unit

      service.tickZone(zoneA, Duration.ofSeconds(1))
      service.tickZone(zoneB, Duration.ofSeconds(1))

      stateA shouldBe
        expectedCombatState(
          playerId = playerA,
          status = CombatStatus.FIGHTING,
          zoneId = zoneA,
          activeTeamId = teamA,
          enemyName = "Training Dummy",
          enemyHp = 950f,
          enemyMaxHp = 1000f,
          members = listOf(expectedCombatMemberState("warrior", 10f, 5f, 9f, 10f)),
          lastSimulatedAt = stateA.lastSimulatedAt,
        )
      stateB shouldBe
        expectedCombatState(
          playerId = playerB,
          status = CombatStatus.FIGHTING,
          zoneId = zoneB,
          activeTeamId = teamB,
          enemyName = "Forest Slime",
          enemyHp = 960f,
          enemyMaxHp = 1000f,
          members = listOf(expectedCombatMemberState("warrior", 8f, 5f, 9f, 10f)),
          lastSimulatedAt = stateB.lastSimulatedAt,
        )
      verify(exactly = 1) { playerStateWorkQueue.markDirty(playerA) }
      verify(exactly = 1) { playerStateWorkQueue.markDirty(playerB) }
    }

    "tick skips disconnected players in zone processing" {
      val playerId = UUID.randomUUID()
      val state =
        expectedSingleMemberCombatState(
          playerId = playerId,
          teamId = UUID.randomUUID(),
          attack = 10f,
          hit = 5f,
          currentHp = 10f,
          maxHp = 10f,
          enemyHp = 1000f,
          enemyMaxHp = 1000f,
        )

      every { combatStateRepository.findActiveByZoneId("starter-plains") } returns listOf(state)
      every { activePlayerRegistry.activePlayerIds() } returns emptySet()

      service.tickZone("starter-plains", Duration.ofSeconds(1))

      verify(exactly = 0) { combatStateRepository.findById(any()) }
      verify(exactly = 0) { combatStateRepository.save(any()) }
      verify(exactly = 0) { playerStateWorkQueue.markDirty(any()) }
    }
  }
}
