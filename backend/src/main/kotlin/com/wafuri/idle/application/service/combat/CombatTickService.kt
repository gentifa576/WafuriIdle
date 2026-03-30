package com.wafuri.idle.application.service.combat

import com.wafuri.idle.application.config.GameConfig
import com.wafuri.idle.application.port.out.ActivePlayerRegistry
import com.wafuri.idle.application.port.out.CombatStateRepository
import com.wafuri.idle.application.port.out.PlayerStateWorkQueue
import com.wafuri.idle.application.service.player.ProgressionService
import com.wafuri.idle.application.service.scaling.ScalingRule
import com.wafuri.idle.domain.model.CombatStatus
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import java.time.Duration
import java.time.Instant
import java.util.UUID

@ApplicationScoped
class CombatTickService(
  private val activePlayerRegistry: ActivePlayerRegistry,
  private val combatStateRepository: CombatStateRepository,
  private val combatStatService: CombatStatService,
  private val playerStateWorkQueue: PlayerStateWorkQueue,
  private val combatLootService: CombatLootService,
  private val progressionService: ProgressionService,
  private val scalingRule: ScalingRule,
  private val gameConfig: GameConfig,
) {
  @Transactional
  fun tickZone(
    zoneId: String,
    elapsed: Duration,
  ) {
    val activePlayerIds = activePlayerRegistry.activePlayerIds()
    combatStateRepository.findActiveByZoneId(zoneId).forEach { state ->
      if (state.playerId in activePlayerIds) {
        tickPlayer(state.playerId, elapsed)
      }
    }
  }

  fun tickPlayer(
    playerId: UUID,
    elapsed: Duration,
  ) {
    val state = combatStateRepository.findById(playerId) ?: return
    if (state.status == CombatStatus.IDLE) {
      return
    }

    val teamStats = combatStatService.teamStatsForPlayer(playerId, state.members)
    val combatConfig = gameConfig.combat()
    val refreshedState = state.refreshTeam(teamStats.teamId, teamStats.toCombatMembers(state.members))
    val advancedState =
      refreshedState.advance(
        elapsedMillis = elapsed.toMillis(),
        damageIntervalMillis = combatConfig.damageInterval().toMillis(),
        respawnDelayMillis = combatConfig.respawnDelay().toMillis(),
      )
    val scaledState = refreshRespawnedEnemy(playerId, refreshedState, advancedState)
    val nextState =
      scaledState.copy(lastSimulatedAt = Instant.now())

    if (nextState != state) {
      combatStateRepository.save(nextState)
      if (state.status != CombatStatus.WON && nextState.status == CombatStatus.WON) {
        val zoneId = requireNotNull(nextState.zoneId) { "Won combat must retain a zone id." }
        progressionService.recordKill(playerId, zoneId, nextState.enemyLevel)
        combatLootService.rollLoot(playerId, zoneId, refreshedState.enemyLevel)
      }
      if (
        nextState.copy(
          pendingDamageMillis = state.pendingDamageMillis,
          pendingRespawnMillis = state.pendingRespawnMillis,
          lastSimulatedAt = state.lastSimulatedAt,
        ) != state
      ) {
        playerStateWorkQueue.markDirty(playerId)
      }
    }
  }

  private fun refreshRespawnedEnemy(
    playerId: UUID,
    previousState: com.wafuri.idle.domain.model.CombatState,
    nextState: com.wafuri.idle.domain.model.CombatState,
  ): com.wafuri.idle.domain.model.CombatState {
    if (previousState.status != CombatStatus.WON || nextState.status != CombatStatus.FIGHTING) {
      return nextState
    }
    val zoneId = requireNotNull(nextState.zoneId) { "Respawned combat must retain a zone id." }
    val zoneLevel = progressionService.requireZoneProgress(playerId, zoneId).level
    val scaledEnemyHp = scalingRule.enemyHpFor(zoneLevel, nextState.enemyBaseHp)
    return nextState.refreshEnemy(enemyLevel = zoneLevel, enemyMaxHp = scaledEnemyHp)
  }
}
