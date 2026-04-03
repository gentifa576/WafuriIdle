package com.wafuri.idle.application.service.player

import com.wafuri.idle.application.config.GameConfig
import com.wafuri.idle.application.model.ZoneLevelUpMessage
import com.wafuri.idle.application.port.out.PlayerMessageQueue
import com.wafuri.idle.application.port.out.PlayerStateWorkQueue
import com.wafuri.idle.application.port.out.PlayerZoneProgressRepository
import com.wafuri.idle.application.port.out.Repository
import com.wafuri.idle.application.service.combat.CombatStatService
import com.wafuri.idle.application.service.scaling.ScalingRule
import com.wafuri.idle.domain.model.Player
import com.wafuri.idle.domain.model.PlayerZoneProgress
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import java.util.UUID
import kotlin.math.roundToInt

@ApplicationScoped
class ProgressionService(
  private val playerRepository: Repository<Player, UUID>,
  private val playerZoneProgressRepository: PlayerZoneProgressRepository,
  private val playerEventQueue: PlayerMessageQueue,
  private val playerStateWorkQueue: PlayerStateWorkQueue,
  private val combatStatService: CombatStatService,
  private val scalingRule: ScalingRule,
  private val gameConfig: GameConfig,
) {
  @Transactional
  fun recordKill(
    playerId: UUID,
    zoneId: String,
    enemyLevel: Int = 1,
  ) {
    val player = playerRepository.require(playerId)

    val config = gameConfig.progression().player()
    val rewardMultiplier = scalingRule.rewardMultiplier(enemyLevel)
    val amount = (config.killExperience() * rewardMultiplier).roundToInt()
    val rewardedPlayer = player.grantExperience(amount, config.experiencePerLevel())
    val updatedPlayer = rewardedPlayer.grantGold((config.killGold() * rewardMultiplier).roundToInt())
    if (updatedPlayer != player) {
      playerRepository.save(updatedPlayer)
      if (updatedPlayer.level != player.level) {
        combatStatService.invalidatePlayer(playerId)
      }
    }

    val currentProgress = requireZoneProgress(playerId, zoneId)
    val updatedProgress = currentProgress.recordKill(gameConfig.progression().zone().killsPerLevel())
    playerZoneProgressRepository.save(updatedProgress)
    if (updatedProgress.level > currentProgress.level) {
      playerEventQueue.enqueue(ZoneLevelUpMessage(playerId, zoneId, updatedProgress.level))
    }

    playerStateWorkQueue.markDirty(playerId)
  }

  fun listZoneProgress(playerId: UUID): List<PlayerZoneProgress> {
    playerRepository.require(playerId)
    return playerZoneProgressRepository.findByPlayerId(playerId)
  }

  fun requirePlayer(playerId: UUID): Player = playerRepository.require(playerId)

  fun requireZoneProgress(
    playerId: UUID,
    zoneId: String,
  ): PlayerZoneProgress =
    playerZoneProgressRepository.findByPlayerIdAndZoneId(playerId, zoneId)
      ?: PlayerZoneProgress(playerId, zoneId)
}
