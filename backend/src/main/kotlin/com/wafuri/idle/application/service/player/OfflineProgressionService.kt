package com.wafuri.idle.application.service.player

import com.wafuri.idle.application.config.GameConfig
import com.wafuri.idle.application.model.OfflineProgressionMessage
import com.wafuri.idle.application.model.OfflineRewardSummary
import com.wafuri.idle.application.port.out.CombatStateRepository
import com.wafuri.idle.application.port.out.PlayerMessageQueue
import com.wafuri.idle.application.service.combat.CombatLootService
import com.wafuri.idle.application.service.combat.CombatStatService
import com.wafuri.idle.application.service.combat.RandomSource
import com.wafuri.idle.application.service.enemy.EnemyTemplateCatalog
import com.wafuri.idle.application.service.scaling.ScalingRule
import com.wafuri.idle.application.service.zone.ZoneTemplateCatalog
import com.wafuri.idle.domain.model.CombatState
import com.wafuri.idle.domain.model.CombatStatus
import com.wafuri.idle.domain.model.PlayerZoneProgress
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.math.roundToInt

@ApplicationScoped
class OfflineProgressionService(
  private val combatStateRepository: CombatStateRepository,
  private val combatStatService: CombatStatService,
  private val progressionService: ProgressionService,
  private val combatLootService: CombatLootService,
  private val playerEventQueue: PlayerMessageQueue,
  private val zoneTemplateCatalog: ZoneTemplateCatalog,
  private val enemyTemplateCatalog: EnemyTemplateCatalog,
  private val randomSource: RandomSource,
  private val scalingRule: ScalingRule,
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
    val refreshedState =
      refreshCombatState(playerId, state) ?: run {
        combatStateRepository.save(CombatState.idle(playerId, lastSimulatedAt = now))
        return null
      }
    val beforePlayer = progressionService.requirePlayer(playerId)
    val beforeZone = progressionService.requireZoneProgress(playerId, zoneId)
    val projection = projectOfflineCombat(refreshedState, offlineDuration, beforeZone)

    projection.killEnemyLevels.forEach { enemyLevel ->
      progressionService.recordKill(playerId, zoneId, enemyLevel)
    }
    val rewardCounts =
      buildMap {
        projection.killEnemyLevels.forEach { enemyLevel ->
          combatLootService.rollLoot(playerId, zoneId, enemyLevel)?.let { item ->
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
        goldGained = afterPlayer.gold - beforePlayer.gold,
        playerLevel = afterPlayer.level,
        playerLevelsGained = afterPlayer.level - beforePlayer.level,
        zoneId = zoneId,
        zoneLevel = afterZone.level,
        zoneLevelsGained = afterZone.level - beforeZone.level,
        rewards = rewards,
      )

    if (offlineDuration >= gameConfig.progression().offline().notifyThreshold() && result.hasGains()) {
      playerEventQueue.enqueue(OfflineProgressionMessage.from(result))
    }

    return result
  }

  private fun refreshCombatState(
    playerId: UUID,
    state: CombatState,
  ): CombatState? {
    val teamStats = combatStatService.teamStatsForPlayerOrNull(playerId, state.members) ?: return null
    return state.refreshTeam(teamStats.teamId, teamStats.toCombatMembers(state.members))
  }

  private fun projectOfflineCombat(
    state: CombatState,
    elapsed: Duration,
    startingZoneProgress: PlayerZoneProgress,
  ): OfflineCombatProjection {
    val params =
      OfflineCombatParams(
        damageIntervalMillis = gameConfig.combat().damageInterval().toMillis(),
        respawnDelayMillis = gameConfig.combat().respawnDelay().toMillis(),
        reviveDelayMillis = gameConfig.combat().reviveDelay().toMillis(),
        reviveHpRatio = gameConfig.combat().reviveHpRatio(),
      )
    var projection = MutableOfflineCombatProjection(state, elapsed.toMillis().coerceAtLeast(0), startingZoneProgress)

    while (projection.canContinue()) {
      projection =
        when (projection.state.status) {
          CombatStatus.WON -> advanceRespawnPhase(projection, params)
          CombatStatus.DOWN -> advanceRevivePhase(projection, params)
          CombatStatus.FIGHTING -> advanceFightPhase(projection, params)
          CombatStatus.IDLE -> projection
        }
    }

    return projection.toResult()
  }

  private fun advanceRespawnPhase(
    projection: MutableOfflineCombatProjection,
    params: OfflineCombatParams,
  ): MutableOfflineCombatProjection {
    val waitMillis = (params.respawnDelayMillis - projection.state.pendingRespawnMillis).coerceAtLeast(0)
    return advanceRecoveryPhase(projection, waitMillis, params)
  }

  private fun advanceRevivePhase(
    projection: MutableOfflineCombatProjection,
    params: OfflineCombatParams,
  ): MutableOfflineCombatProjection {
    val waitMillis = (params.reviveDelayMillis - projection.state.pendingReviveMillis).coerceAtLeast(0)
    return advanceRecoveryPhase(projection, waitMillis, params)
  }

  private fun advanceRecoveryPhase(
    projection: MutableOfflineCombatProjection,
    waitMillis: Long,
    params: OfflineCombatParams,
  ): MutableOfflineCombatProjection {
    val elapsedMillis = minOf(projection.remainingMillis, waitMillis)
    val advanced =
      projection.state.advance(
        elapsedMillis,
        params.damageIntervalMillis,
        params.respawnDelayMillis,
        params.reviveDelayMillis,
        params.reviveHpRatio,
      )
    val recovered =
      if (elapsedMillis == waitMillis && advanced.status == CombatStatus.FIGHTING) {
        refreshEnemyForZoneLevel(advanced, projection.zoneProgress.level)
      } else {
        advanced
      }
    return projection.copy(state = recovered, remainingMillis = projection.remainingMillis - elapsedMillis)
  }

  private fun advanceFightPhase(
    projection: MutableOfflineCombatProjection,
    params: OfflineCombatParams,
  ): MutableOfflineCombatProjection {
    val killTimeMillis = timeToKillMillis(projection.state, params.damageIntervalMillis)
    val elapsedMillis =
      when {
        killTimeMillis == Long.MAX_VALUE -> projection.remainingMillis
        projection.remainingMillis < killTimeMillis -> projection.remainingMillis
        else -> killTimeMillis
      }
    val advanced =
      projection.state.advance(
        elapsedMillis,
        params.damageIntervalMillis,
        params.respawnDelayMillis,
        params.reviveDelayMillis,
        params.reviveHpRatio,
      )
    val defeatedEnemy = projection.state.enemyHp > 0f && advanced.enemyHp == 0f
    return if (!defeatedEnemy) {
      projection.copy(state = advanced, remainingMillis = projection.remainingMillis - elapsedMillis)
    } else {
      val zoneProgressGain =
        (gameConfig.progression().zone().progressMultiplier() * scalingRule.rewardMultiplier(projection.zoneProgress.level))
          .roundToInt()
          .coerceAtLeast(1)
      projection.recordKill(advanced, elapsedMillis, zoneProgressGain, scalingRule::zoneLevelForKillCount)
    }
  }

  private fun refreshEnemyForZoneLevel(
    state: CombatState,
    zoneLevel: Int,
  ): CombatState {
    val zoneId = requireNotNull(state.zoneId) { "Active combat must keep a zone id." }
    val enemy = enemyTemplateCatalog.requireRandom(zoneTemplateCatalog.require(zoneId).enemies, randomSource)
    val enemyMaxHp = scalingRule.enemyHpFor(zoneLevel, enemy.baseHp)
    val enemyAttack = scalingRule.enemyAttackFor(zoneLevel, enemy.attack)
    return state.refreshEnemy(enemy.id, enemy.name, enemy.image, enemy.baseHp, zoneLevel, enemyAttack, enemyMaxHp)
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

private data class OfflineCombatParams(
  val damageIntervalMillis: Long,
  val respawnDelayMillis: Long,
  val reviveDelayMillis: Long,
  val reviveHpRatio: Float,
)

private data class MutableOfflineCombatProjection(
  val state: CombatState,
  val remainingMillis: Long,
  val zoneProgress: PlayerZoneProgress,
  val kills: Int = 0,
  val killEnemyLevels: List<Int> = emptyList(),
) {
  fun canContinue(): Boolean = remainingMillis > 0 && state.status != CombatStatus.IDLE

  fun recordKill(
    nextState: CombatState,
    elapsedMillis: Long,
    zoneProgressGain: Int,
    levelForKillCount: (Int) -> Int,
  ): MutableOfflineCombatProjection =
    copy(
      state = nextState,
      remainingMillis = remainingMillis - elapsedMillis,
      zoneProgress = zoneProgress.recordKills(zoneProgressGain, levelForKillCount),
      kills = kills + 1,
      killEnemyLevels = killEnemyLevels + nextState.enemyLevel,
    )

  fun toResult(): OfflineCombatProjection = OfflineCombatProjection(state, kills, killEnemyLevels)
}

data class OfflineProgressionResult(
  val playerId: UUID,
  val offlineDuration: Duration,
  val kills: Int,
  val experienceGained: Int,
  val goldGained: Int,
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
      goldGained > 0 ||
      playerLevelsGained > 0 ||
      zoneLevelsGained > 0 ||
      rewards.isNotEmpty()
}

private data class OfflineCombatProjection(
  val state: CombatState,
  val kills: Int,
  val killEnemyLevels: List<Int>,
)
