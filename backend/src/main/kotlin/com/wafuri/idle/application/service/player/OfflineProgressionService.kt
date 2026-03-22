package com.wafuri.idle.application.service.player

import com.wafuri.idle.application.config.GameConfig
import com.wafuri.idle.application.model.OfflineProgressionMessage
import com.wafuri.idle.application.model.OfflineRewardSummary
import com.wafuri.idle.application.port.out.CombatStateRepository
import com.wafuri.idle.application.port.out.PlayerMessageQueue
import com.wafuri.idle.application.service.combat.CombatLootService
import com.wafuri.idle.application.service.combat.CombatStatService
import com.wafuri.idle.domain.model.CombatState
import com.wafuri.idle.domain.model.CombatStatus
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import java.time.Duration
import java.time.Instant
import java.util.UUID

@ApplicationScoped
class OfflineProgressionService(
  private val combatStateRepository: CombatStateRepository,
  private val combatStatService: CombatStatService,
  private val progressionService: ProgressionService,
  private val combatLootService: CombatLootService,
  private val playerEventQueue: PlayerMessageQueue,
  private val gameConfig: GameConfig,
) {
  @Transactional
  fun applyIfNeeded(playerId: UUID): OfflineProgressionResult? {
    val state = combatStateRepository.findById(playerId) ?: return null
    if (state.status == CombatStatus.IDLE) {
      return null
    }

    val now = Instant.now()
    val lastSimulatedAt =
      state.lastSimulatedAt ?: run {
        combatStateRepository.save(state.copy(lastSimulatedAt = now))
        return null
      }
    val offlineDuration = Duration.between(lastSimulatedAt, now).coerceAtLeast(Duration.ZERO)
    if (offlineDuration.isZero) {
      return null
    }

    val zoneId = requireNotNull(state.zoneId) { "Active combat must keep a zone id." }
    val beforePlayer = progressionService.requirePlayer(playerId)
    val beforeZone = progressionService.requireZoneProgress(playerId, zoneId)
    val refreshedState = refreshCombatState(playerId, state)
    val projection = projectOfflineCombat(refreshedState, offlineDuration)

    repeat(projection.kills) {
      progressionService.recordKill(playerId, zoneId)
    }
    val rewardCounts =
      buildMap<String, Int> {
        repeat(projection.kills) {
          combatLootService.rollLoot(playerId, zoneId)?.let { item ->
            put(item.item.name, getOrDefault(item.item.name, 0) + 1)
          }
        }
      }
    val rewards =
      rewardCounts.entries
        .sortedBy { it.key }
        .map { (itemName, count) -> OfflineRewardSummary(itemName = itemName, count = count) }

    val afterPlayer = progressionService.requirePlayer(playerId)
    val afterZone = progressionService.requireZoneProgress(playerId, zoneId)
    val finalState = projection.state.copy(lastSimulatedAt = now)
    combatStateRepository.save(finalState)

    val result =
      OfflineProgressionResult(
        playerId = playerId,
        offlineDuration = offlineDuration,
        kills = projection.kills,
        experienceGained = afterPlayer.experience - beforePlayer.experience,
        playerLevel = afterPlayer.level,
        playerLevelsGained = afterPlayer.level - beforePlayer.level,
        zoneId = zoneId,
        zoneLevel = afterZone.level,
        zoneLevelsGained = afterZone.level - beforeZone.level,
        rewards = rewards,
      )

    if (offlineDuration >= gameConfig.progression().offline().notifyThreshold() && result.hasGains()) {
      playerEventQueue.enqueue(
        OfflineProgressionMessage(
          playerId = playerId,
          offlineDurationMillis = offlineDuration.toMillis(),
          kills = result.kills,
          experienceGained = result.experienceGained,
          playerLevel = result.playerLevel,
          playerLevelsGained = result.playerLevelsGained,
          zoneId = result.zoneId,
          zoneLevel = result.zoneLevel,
          zoneLevelsGained = result.zoneLevelsGained,
          rewards = result.rewards,
        ),
      )
    }

    return result
  }

  private fun refreshCombatState(
    playerId: UUID,
    state: CombatState,
  ): CombatState {
    val teamStats = combatStatService.teamStatsForPlayer(playerId, state.members)
    return state.refreshTeam(teamStats.teamId, teamStats.toCombatMembers(state.members))
  }

  private fun projectOfflineCombat(
    state: CombatState,
    elapsed: Duration,
  ): OfflineCombatProjection {
    val combatConfig = gameConfig.combat()
    var remaining = elapsed.toMillis().coerceAtLeast(0)
    var current = state
    var kills = 0
    val damageIntervalMillis = combatConfig.damageInterval().toMillis()
    val respawnDelayMillis = combatConfig.respawnDelay().toMillis()

    while (remaining > 0 && current.status != CombatStatus.IDLE) {
      if (current.status == CombatStatus.WON) {
        val toRespawn = (respawnDelayMillis - current.pendingRespawnMillis).coerceAtLeast(0)
        if (remaining < toRespawn) {
          current =
            current.advance(
              elapsedMillis = remaining,
              damageIntervalMillis = damageIntervalMillis,
              respawnDelayMillis = respawnDelayMillis,
            )
          remaining = 0
        } else {
          current =
            current.advance(
              elapsedMillis = toRespawn,
              damageIntervalMillis = damageIntervalMillis,
              respawnDelayMillis = respawnDelayMillis,
            )
          remaining -= toRespawn
        }
        continue
      }

      val killTimeMillis = timeToKillMillis(current, damageIntervalMillis)
      if (killTimeMillis == Long.MAX_VALUE) {
        current =
          current.advance(
            elapsedMillis = remaining,
            damageIntervalMillis = damageIntervalMillis,
            respawnDelayMillis = respawnDelayMillis,
          )
        remaining = 0
        continue
      }
      if (remaining < killTimeMillis) {
        current =
          current.advance(
            elapsedMillis = remaining,
            damageIntervalMillis = damageIntervalMillis,
            respawnDelayMillis = respawnDelayMillis,
          )
        remaining = 0
        continue
      }

      current =
        current.advance(
          elapsedMillis = killTimeMillis,
          damageIntervalMillis = damageIntervalMillis,
          respawnDelayMillis = respawnDelayMillis,
        )
      remaining -= killTimeMillis
      if (current.status == CombatStatus.WON) {
        kills += 1
      }
    }

    return OfflineCombatProjection(state = current, kills = kills)
  }

  private fun timeToKillMillis(
    state: CombatState,
    damageIntervalMillis: Long,
  ): Long {
    val damagePerStep = state.teamDps * (damageIntervalMillis / 1000f)
    if (damagePerStep <= 0f) {
      return Long.MAX_VALUE
    }
    val stepsToKill =
      kotlin.math
        .ceil(state.enemyHp / damagePerStep)
        .toLong()
        .coerceAtLeast(1L)
    val firstStepDelay =
      if (state.pendingDamageMillis == 0L) {
        damageIntervalMillis
      } else {
        damageIntervalMillis - state.pendingDamageMillis
      }
    return firstStepDelay + ((stepsToKill - 1L) * damageIntervalMillis)
  }
}

data class OfflineProgressionResult(
  val playerId: UUID,
  val offlineDuration: Duration,
  val kills: Int,
  val experienceGained: Int,
  val playerLevel: Int,
  val playerLevelsGained: Int,
  val zoneId: String,
  val zoneLevel: Int,
  val zoneLevelsGained: Int,
  val rewards: List<OfflineRewardSummary>,
) {
  fun hasGains(): Boolean =
    kills > 0 ||
      experienceGained > 0 ||
      playerLevelsGained > 0 ||
      zoneLevelsGained > 0 ||
      rewards.isNotEmpty()
}

private data class OfflineCombatProjection(
  val state: CombatState,
  val kills: Int,
)
