package com.wafuri.idle.tests.support

import com.wafuri.idle.application.port.out.CombatStateRepository
import com.wafuri.idle.application.service.combat.CombatTickService
import com.wafuri.idle.domain.model.CombatState
import com.wafuri.idle.domain.model.CombatStatus
import jakarta.enterprise.context.ApplicationScoped
import java.time.Duration
import java.util.UUID

@ApplicationScoped
class TestTickWarpService(
  private val combatStateRepository: CombatStateRepository,
  private val combatTickService: CombatTickService,
) {
  fun warpCombat(
    playerId: UUID,
    elapsed: Duration,
  ): CombatState {
    combatTickService.tickPlayer(playerId, elapsed)
    return requireState(playerId)
  }

  fun warpCombatUntilStatus(
    playerId: UUID,
    status: CombatStatus,
    step: Duration = Duration.ofSeconds(1),
    maxSteps: Int = 100,
  ): CombatState {
    repeat(maxSteps) {
      val state = requireState(playerId)
      if (state.status == status) {
        return state
      }
      warpCombat(playerId, step)
    }
    error("Combat for player $playerId did not reach status $status within $maxSteps warp steps.")
  }

  fun warpCombatWins(
    playerId: UUID,
    wins: Int,
    killStep: Duration = Duration.ofSeconds(12),
    respawnStep: Duration = Duration.ofSeconds(1),
  ): CombatState {
    require(wins > 0) { "Win count must be positive." }

    repeat(wins) { index ->
      warpCombatUntilStatus(
        playerId = playerId,
        status = CombatStatus.WON,
        step = killStep,
        maxSteps = 100,
      )
      if (index < wins - 1) {
        warpCombatUntilStatus(
          playerId = playerId,
          status = CombatStatus.FIGHTING,
          step = respawnStep,
          maxSteps = 10,
        )
      }
    }

    return requireState(playerId)
  }

  private fun requireState(playerId: UUID): CombatState =
    requireNotNull(combatStateRepository.findById(playerId)) { "Combat state for player $playerId was not found." }
}
