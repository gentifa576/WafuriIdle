package com.wafuri.idle.application.service.combat

import com.wafuri.idle.application.config.GameConfig
import com.wafuri.idle.application.port.out.ActivePlayerRegistry
import com.wafuri.idle.application.port.out.CombatStateRepository
import com.wafuri.idle.application.port.out.PlayerStateWorkQueue
import com.wafuri.idle.application.service.player.ProgressionService
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
    val nextState =
      advancedState.copy(lastSimulatedAt = Instant.now())

    if (nextState != state) {
      combatStateRepository.save(nextState)
      if (state.status != CombatStatus.WON && nextState.status == CombatStatus.WON) {
        val zoneId = requireNotNull(nextState.zoneId) { "Won combat must retain a zone id." }
        progressionService.recordKill(playerId, zoneId)
        combatLootService.rollLoot(playerId, zoneId)
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
}
