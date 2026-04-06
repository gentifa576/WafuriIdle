package com.wafuri.idle.application.service.combat

import com.wafuri.idle.application.config.GameConfig
import com.wafuri.idle.application.port.out.ActivePlayerRegistry
import com.wafuri.idle.application.port.out.CombatStateRepository
import com.wafuri.idle.application.port.out.PlayerStateWorkQueue
import com.wafuri.idle.application.service.enemy.EnemyTemplateCatalog
import com.wafuri.idle.application.service.player.ProgressionService
import com.wafuri.idle.application.service.runInNewTransaction
import com.wafuri.idle.application.service.scaling.ScalingRule
import com.wafuri.idle.application.service.zone.ZoneTemplateCatalog
import com.wafuri.idle.domain.model.CombatState
import com.wafuri.idle.domain.model.CombatStatus
import jakarta.enterprise.context.ApplicationScoped
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.slf4j.LoggerFactory
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
  private val zoneTemplateCatalog: ZoneTemplateCatalog,
  private val enemyTemplateCatalog: EnemyTemplateCatalog,
  private val randomSource: RandomSource,
  private val scalingRule: ScalingRule,
  private val gameConfig: GameConfig,
) {
  private val logger = LoggerFactory.getLogger(CombatTickService::class.java)

  suspend fun tickZone(
    zoneId: String,
    elapsed: Duration,
  ) {
    coroutineScope {
      val activePlayerIds = activePlayerRegistry.activePlayerIds()
      val combatConfig = gameConfig.combat()
      val now = Instant.now()
      combatStateRepository
        .findActiveByZoneId(zoneId)
        .mapNotNull { state ->
          state.playerId
            .takeIf { it in activePlayerIds }
            ?.let { playerId ->
              async {
                runCatching {
                  runInNewTransaction {
                    tickPlayerTransactional(playerId, elapsed, combatConfig, now)
                  }
                }.onFailure { exception ->
                  logger
                    .atError()
                    .setCause(exception)
                    .addKeyValue("zoneId", zoneId)
                    .addKeyValue("playerId", playerId)
                    .addKeyValue("elapsedMillis", elapsed.toMillis())
                    .log("Player combat tick failed.")
                }
              }
            }
        }.awaitAll()
    }
  }

  private fun tickPlayerTransactional(
    playerId: UUID,
    elapsed: Duration,
    combatConfig: GameConfig.Combat,
    now: Instant,
  ) {
    val state = combatStateRepository.findById(playerId) ?: return
    if (state.status == CombatStatus.IDLE) {
      return
    }

    val teamStats =
      combatStatService.teamStatsForPlayerOrNull(playerId, state.members)
        ?: run {
          combatStateRepository.save(CombatState.idle(playerId, lastSimulatedAt = now))
          playerStateWorkQueue.markDirty(playerId)
          return
        }
    val refreshedState = state.refreshTeam(teamStats.teamId, teamStats.toCombatMembers(state.members))
    val advancedState =
      refreshedState.advance(
        elapsed.toMillis(),
        combatConfig.damageInterval().toMillis(),
        combatConfig.respawnDelay().toMillis(),
        combatConfig.reviveDelay().toMillis(),
        combatConfig.reviveHpRatio(),
      )
    val scaledState = refreshRespawnedEnemy(playerId, refreshedState, advancedState)
    val nextState = scaledState.copy(lastSimulatedAt = now)

    if (nextState != state) {
      combatStateRepository.save(nextState)
      if (state.enemyHp > 0f && nextState.enemyHp == 0f) {
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
    previousState: CombatState,
    nextState: CombatState,
  ): CombatState {
    if (previousState.status !in setOf(CombatStatus.WON, CombatStatus.DOWN) || nextState.status != CombatStatus.FIGHTING) {
      return nextState
    }
    val zoneId = requireNotNull(nextState.zoneId) { "Respawned combat must retain a zone id." }
    val zoneLevel = progressionService.requireZoneProgress(playerId, zoneId).level
    val enemy = enemyTemplateCatalog.requireRandom(zoneTemplateCatalog.require(zoneId).enemies, randomSource)
    val scaledEnemyHp = scalingRule.enemyHpFor(zoneLevel, enemy.baseHp)
    val scaledEnemyAttack = scalingRule.enemyAttackFor(zoneLevel, enemy.attack)
    return nextState.refreshEnemy(enemy.id, enemy.name, enemy.image, enemy.baseHp, zoneLevel, scaledEnemyAttack, scaledEnemyHp)
  }
}
