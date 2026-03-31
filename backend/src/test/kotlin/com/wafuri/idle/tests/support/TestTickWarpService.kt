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
    killStep: Duration = Duration.ofSeconds(1),
    respawnStep: Duration = Duration.ofSeconds(1),
    reviveStep: Duration = Duration.ofSeconds(1),
  ): CombatState {
    require(wins > 0) { "Win count must be positive." }

    repeat(wins) { index ->
      var defeated = false
      repeat(10_000) {
        val state = requireState(playerId)
        if (state.enemyHp == 0f) {
          defeated = true
          return@repeat
        }
        warpCombat(playerId, killStep)
      }
      val finalState = requireState(playerId)
      check(defeated || finalState.enemyHp == 0f) {
        "Combat for player $playerId did not defeat the enemy within the allotted warp steps. Final state: $finalState"
      }
      if (index < wins - 1) {
        var state = requireState(playerId)
        while (state.status != CombatStatus.FIGHTING) {
          state =
            when (state.status) {
              CombatStatus.WON -> warpCombat(playerId, respawnStep)
              CombatStatus.DOWN -> warpCombat(playerId, reviveStep)
              else -> error("Unexpected combat status while warping wins: ${state.status}")
            }
        }
      }
    }

    return requireState(playerId)
  }

  private fun requireState(playerId: UUID): CombatState =
    requireNotNull(combatStateRepository.findById(playerId)) { "Combat state for player $playerId was not found." }
}
