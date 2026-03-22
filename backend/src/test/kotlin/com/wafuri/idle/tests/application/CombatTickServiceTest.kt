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
import com.wafuri.idle.domain.model.CombatMemberState
import com.wafuri.idle.domain.model.CombatState
import com.wafuri.idle.domain.model.CombatStatus
import com.wafuri.idle.tests.support.gameConfig
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.floats.plusOrMinus
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
          config,
        )
    }

    "tick advances enemy hp when enough elapsed time has accumulated" {
      val playerId = UUID.randomUUID()
      val teamId = UUID.randomUUID()
      val current =
        CombatState(
          playerId = playerId,
          status = CombatStatus.FIGHTING,
          zoneId = "starter-plains",
          activeTeamId = teamId,
          enemyName = "Training Dummy",
          enemyHp = 1000f,
          enemyMaxHp = 1000f,
          members =
            listOf(
              CombatMemberState(
                characterKey = "warrior",
                attack = 12f,
                hit = 7f,
                currentHp = 11f,
                maxHp = 11f,
              ),
            ),
        )
      var savedState: CombatState? = null

      every { combatStateRepository.findActiveByZoneId("starter-plains") } returns listOf(current)
      every { activePlayerRegistry.activePlayerIds() } returns setOf(playerId)
      every { combatStateRepository.findById(playerId) } returns current
      every { combatStatService.teamStatsForPlayer(playerId, current.members) } returns
        TeamCombatStats(
          teamId,
          listOf(CharacterCombatStats("warrior", 12f, 7f, 11f)),
        )
      every { combatStateRepository.save(any()) } answers {
        firstArg<CombatState>().also { savedState = it }
      }
      every { playerStateWorkQueue.markDirty(playerId) } just runs
      every { combatLootService.rollLoot(any(), any()) } returns null
      every { progressionService.recordKill(any(), any()) } returns Unit

      service.tickZone("starter-plains", Duration.ofMillis(200))

      savedState?.enemyHp shouldBe 1000f
      savedState?.pendingDamageMillis shouldBe 200L
      verify(exactly = 0) { playerStateWorkQueue.markDirty(playerId) }

      service.tickZone("starter-plains", Duration.ofSeconds(1))

      savedState?.enemyHp shouldBe (916f plusOrMinus 0.001f)
      savedState?.teamDps shouldBe (84f plusOrMinus 0.001f)
      savedState?.members?.single()?.currentHp shouldBe 11f
      verify(exactly = 1) { playerStateWorkQueue.markDirty(playerId) }
    }

    "tick self corrects when elapsed time drifts past the damage cadence" {
      val playerId = UUID.randomUUID()
      val teamId = UUID.randomUUID()
      var currentState =
        CombatState(
          playerId = playerId,
          status = CombatStatus.FIGHTING,
          zoneId = "starter-plains",
          activeTeamId = teamId,
          enemyName = "Training Dummy",
          enemyHp = 1000f,
          enemyMaxHp = 1000f,
          members =
            listOf(
              CombatMemberState(
                characterKey = "warrior",
                attack = 12f,
                hit = 7f,
                currentHp = 11f,
                maxHp = 11f,
              ),
            ),
        )

      every { combatStateRepository.findActiveByZoneId("starter-plains") } answers { listOf(currentState) }
      every { activePlayerRegistry.activePlayerIds() } returns setOf(playerId)
      every { combatStateRepository.findById(playerId) } answers { currentState }
      every { combatStatService.teamStatsForPlayer(playerId, currentState.members) } returns
        TeamCombatStats(
          teamId,
          listOf(CharacterCombatStats("warrior", 12f, 7f, 11f)),
        )
      every { combatStateRepository.save(any()) } answers {
        firstArg<CombatState>().also { currentState = it }
      }
      every { playerStateWorkQueue.markDirty(playerId) } just runs
      every { combatLootService.rollLoot(any(), any()) } returns null
      every { progressionService.recordKill(any(), any()) } returns Unit

      service.tickZone("starter-plains", Duration.ofMillis(1200))

      currentState.enemyHp shouldBe (916f plusOrMinus 0.001f)
      currentState.pendingDamageMillis shouldBe 200L

      service.tickZone("starter-plains", Duration.ofMillis(800))

      currentState.enemyHp shouldBe (832f plusOrMinus 0.001f)
      currentState.pendingDamageMillis shouldBe 0L
      verify(exactly = 2) { playerStateWorkQueue.markDirty(playerId) }
    }

    "tick respawns the enemy after the configured delay and resumes combat" {
      val playerId = UUID.randomUUID()
      val teamId = UUID.randomUUID()
      var currentState =
        CombatState(
          playerId = playerId,
          status = CombatStatus.WON,
          zoneId = "starter-plains",
          activeTeamId = teamId,
          enemyName = "Training Dummy",
          enemyHp = 0f,
          enemyMaxHp = 1000f,
          members =
            listOf(
              CombatMemberState(
                characterKey = "warrior",
                attack = 12f,
                hit = 7f,
                currentHp = 11f,
                maxHp = 11f,
              ),
            ),
        )

      every { combatStateRepository.findActiveByZoneId("starter-plains") } answers { listOf(currentState) }
      every { activePlayerRegistry.activePlayerIds() } returns setOf(playerId)
      every { combatStateRepository.findById(playerId) } answers { currentState }
      every { combatStatService.teamStatsForPlayer(playerId, currentState.members) } returns
        TeamCombatStats(
          teamId,
          listOf(CharacterCombatStats("warrior", 12f, 7f, 11f)),
        )
      every { combatStateRepository.save(any()) } answers {
        firstArg<CombatState>().also { currentState = it }
      }
      every { playerStateWorkQueue.markDirty(playerId) } just runs
      every { combatLootService.rollLoot(any(), any()) } returns null
      every { progressionService.recordKill(any(), any()) } returns Unit

      service.tickZone("starter-plains", Duration.ofMillis(500))

      currentState.status shouldBe CombatStatus.WON
      currentState.pendingRespawnMillis shouldBe 500L
      verify(exactly = 0) { playerStateWorkQueue.markDirty(playerId) }

      service.tickZone("starter-plains", Duration.ofMillis(700))

      currentState.status shouldBe CombatStatus.FIGHTING
      currentState.enemyHp shouldBe 1000f
      currentState.pendingRespawnMillis shouldBe 0L
      currentState.pendingDamageMillis shouldBe 200L
      verify(exactly = 1) { playerStateWorkQueue.markDirty(playerId) }
      verify(exactly = 0) { progressionService.recordKill(playerId, "starter-plains") }
      verify(exactly = 0) { combatLootService.rollLoot(playerId, "starter-plains") }
    }

    "tick awards kill progression and loot when combat is first won" {
      val playerId = UUID.randomUUID()
      val teamId = UUID.randomUUID()
      var currentState =
        CombatState(
          playerId = playerId,
          status = CombatStatus.FIGHTING,
          zoneId = "starter-plains",
          activeTeamId = teamId,
          enemyName = "Training Dummy",
          enemyHp = 10f,
          enemyMaxHp = 10f,
          members =
            listOf(
              CombatMemberState(
                characterKey = "warrior",
                attack = 12f,
                hit = 7f,
                currentHp = 11f,
                maxHp = 11f,
              ),
            ),
        )

      every { combatStateRepository.findActiveByZoneId("starter-plains") } answers { listOf(currentState) }
      every { activePlayerRegistry.activePlayerIds() } returns setOf(playerId)
      every { combatStateRepository.findById(playerId) } answers { currentState }
      every { combatStatService.teamStatsForPlayer(playerId, currentState.members) } returns
        TeamCombatStats(
          teamId,
          listOf(CharacterCombatStats("warrior", 12f, 7f, 11f)),
        )
      every { combatStateRepository.save(any()) } answers {
        firstArg<CombatState>().also { currentState = it }
      }
      every { playerStateWorkQueue.markDirty(playerId) } just runs
      every { combatLootService.rollLoot(playerId, "starter-plains") } returns null
      every { progressionService.recordKill(playerId, "starter-plains") } returns Unit

      service.tickZone("starter-plains", Duration.ofSeconds(1))

      currentState.status shouldBe CombatStatus.WON
      currentState.enemyHp shouldBe 0f
      verify(exactly = 1) { progressionService.recordKill(playerId, "starter-plains") }
      verify(exactly = 1) { combatLootService.rollLoot(playerId, "starter-plains") }
    }

    "tick refreshes combat members when team composition changes" {
      val playerId = UUID.randomUUID()
      val teamId = UUID.randomUUID()
      var currentState =
        CombatState(
          playerId = playerId,
          status = CombatStatus.FIGHTING,
          zoneId = "starter-plains",
          activeTeamId = teamId,
          enemyName = "Training Dummy",
          enemyHp = 1000f,
          enemyMaxHp = 1000f,
          members = listOf(CombatMemberState("warrior", 12f, 7f, 11f, 11f)),
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
      every { combatLootService.rollLoot(any(), any()) } returns null
      every { progressionService.recordKill(any(), any()) } returns Unit

      service.tickZone("starter-plains", Duration.ZERO)

      currentState.members.map { it.characterKey } shouldBe listOf("warrior", "cleric")
      verify(exactly = 1) { playerStateWorkQueue.markDirty(playerId) }
    }

    "tick refreshes active team id when player switches active teams during combat" {
      val playerId = UUID.randomUUID()
      val originalTeamId = UUID.randomUUID()
      val newTeamId = UUID.randomUUID()
      var currentState =
        CombatState(
          playerId = playerId,
          status = CombatStatus.FIGHTING,
          zoneId = "starter-plains",
          activeTeamId = originalTeamId,
          enemyName = "Training Dummy",
          enemyHp = 1000f,
          enemyMaxHp = 1000f,
          members = listOf(CombatMemberState("warrior", 12f, 7f, 11f, 11f)),
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
      every { combatLootService.rollLoot(any(), any()) } returns null
      every { progressionService.recordKill(any(), any()) } returns Unit

      service.tickZone("starter-plains", Duration.ZERO)

      currentState.activeTeamId shouldBe newTeamId
      currentState.members.map { it.characterKey } shouldBe listOf("cleric")
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
        CombatState(
          playerId = playerA,
          status = CombatStatus.FIGHTING,
          zoneId = zoneA,
          activeTeamId = teamA,
          enemyName = "Training Dummy",
          enemyHp = 1000f,
          enemyMaxHp = 1000f,
          members = listOf(CombatMemberState("warrior", 10f, 5f, 10f, 10f)),
        )
      var stateB =
        CombatState(
          playerId = playerB,
          status = CombatStatus.FIGHTING,
          zoneId = zoneB,
          activeTeamId = teamB,
          enemyName = "Forest Slime",
          enemyHp = 1000f,
          enemyMaxHp = 1000f,
          members = listOf(CombatMemberState("warrior", 8f, 5f, 10f, 10f)),
        )

      every { combatStateRepository.findActiveByZoneId(zoneA) } answers { listOf(stateA) }
      every { combatStateRepository.findActiveByZoneId(zoneB) } answers { listOf(stateB) }
      every { activePlayerRegistry.activePlayerIds() } returns setOf(playerA, playerB)
      every { combatStateRepository.findById(playerA) } answers { stateA }
      every { combatStateRepository.findById(playerB) } answers { stateB }
      every { combatStatService.teamStatsForPlayer(playerA, stateA.members) } returns
        TeamCombatStats(teamA, listOf(CharacterCombatStats("warrior", 10f, 5f, 10f)))
      every { combatStatService.teamStatsForPlayer(playerB, stateB.members) } returns
        TeamCombatStats(teamB, listOf(CharacterCombatStats("warrior", 8f, 5f, 10f)))
      every { combatStateRepository.save(match { it.playerId == playerA }) } answers {
        firstArg<CombatState>().also { stateA = it }
      }
      every { combatStateRepository.save(match { it.playerId == playerB }) } answers {
        firstArg<CombatState>().also { stateB = it }
      }
      every { playerStateWorkQueue.markDirty(any()) } just runs
      every { combatLootService.rollLoot(any(), any()) } returns null
      every { progressionService.recordKill(any(), any()) } returns Unit

      service.tickZone(zoneA, Duration.ofSeconds(1))
      service.tickZone(zoneB, Duration.ofSeconds(1))

      stateA.enemyHp shouldBe (950f plusOrMinus 0.001f)
      stateB.enemyHp shouldBe (960f plusOrMinus 0.001f)
      verify(exactly = 1) { playerStateWorkQueue.markDirty(playerA) }
      verify(exactly = 1) { playerStateWorkQueue.markDirty(playerB) }
    }

    "tick skips disconnected players in zone processing" {
      val playerId = UUID.randomUUID()
      val state =
        CombatState(
          playerId = playerId,
          status = CombatStatus.FIGHTING,
          zoneId = "starter-plains",
          activeTeamId = UUID.randomUUID(),
          enemyName = "Training Dummy",
          enemyHp = 1000f,
          enemyMaxHp = 1000f,
          members = listOf(CombatMemberState("warrior", 10f, 5f, 10f, 10f)),
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
