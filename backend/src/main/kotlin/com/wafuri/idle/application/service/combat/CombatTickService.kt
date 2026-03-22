package com.wafuri.idle.application.service.combat

import com.wafuri.idle.application.config.GameConfig
import com.wafuri.idle.application.port.out.CombatStateRepository
import com.wafuri.idle.application.port.out.PlayerStateWorkQueue
import com.wafuri.idle.domain.model.CombatStatus
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import java.time.Duration
import java.util.UUID

@ApplicationScoped
class CombatTickService(
  private val combatStateRepository: CombatStateRepository,
  private val combatStatService: CombatStatService,
  private val playerStateWorkQueue: PlayerStateWorkQueue,
  private val gameConfig: GameConfig,
) {
  @Transactional
  fun tickZone(
    zoneId: String,
    elapsed: Duration,
  ) {
    combatStateRepository.findActiveByZoneId(zoneId).forEach { state ->
      tickCombat(state.playerId, elapsed)
    }
  }

  private fun tickCombat(
    playerId: UUID,
    elapsed: Duration,
  ) {
    val state = combatStateRepository.findById(playerId) ?: return
    if (state.status == CombatStatus.IDLE) {
      return
    }

    val teamStats = combatStatService.teamStatsForPlayer(playerId)
    val combatConfig = gameConfig.combat()
    val nextState =
      state
        .refreshMembers(teamStats.toCombatMembers(state.members))
        .advance(
          elapsedMillis = elapsed.toMillis(),
          damageIntervalMillis = combatConfig.damageInterval().toMillis(),
          respawnDelayMillis = combatConfig.respawnDelay().toMillis(),
        )

    if (nextState != state) {
      combatStateRepository.save(nextState)
      if (
        nextState.copy(
          pendingDamageMillis = state.pendingDamageMillis,
          pendingRespawnMillis = state.pendingRespawnMillis,
        ) != state
      ) {
        playerStateWorkQueue.markDirty(playerId)
      }
    }
  }
}
