package com.wafuri.idle.application.service.combat

import com.wafuri.idle.application.config.GameConfig
import com.wafuri.idle.application.exception.ResourceNotFoundException
import com.wafuri.idle.application.model.CombatSnapshot
import com.wafuri.idle.application.model.toSnapshot
import com.wafuri.idle.application.port.out.CombatStateRepository
import com.wafuri.idle.application.port.out.PlayerStateWorkQueue
import com.wafuri.idle.application.port.out.Repository
import com.wafuri.idle.application.service.zone.ZoneTemplateCatalog
import com.wafuri.idle.domain.model.CombatState
import com.wafuri.idle.domain.model.Player
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import java.time.Instant
import java.util.UUID

@ApplicationScoped
class CombatService(
  private val playerRepository: Repository<Player, UUID>,
  private val combatStateRepository: CombatStateRepository,
  private val combatStatService: CombatStatService,
  private val playerStateWorkQueue: PlayerStateWorkQueue,
  private val zoneTemplateCatalog: ZoneTemplateCatalog,
  private val gameConfig: GameConfig,
) {
  @Transactional
  fun start(playerId: UUID): CombatSnapshot {
    playerRepository.findById(playerId)
      ?: throw ResourceNotFoundException("Player $playerId was not found.")
    val teamStats = combatStatService.teamStatsForPlayer(playerId)
    val zone = zoneTemplateCatalog.default()
    val currentState = combatStateRepository.findById(playerId) ?: CombatState(playerId = playerId)
    val startedState =
      currentState.start(
        zoneId = zone.id,
        teamId = teamStats.teamId,
        enemyName = zone.enemies.first(),
        enemyMaxHp = gameConfig.combat().enemyMaxHp(),
        members = teamStats.toCombatMembers(),
      )
    val nextState = startedState.copy(lastSimulatedAt = Instant.now())
    val savedState = combatStateRepository.save(nextState)
    playerStateWorkQueue.markDirty(playerId)
    return savedState.toSnapshot()
  }
}
