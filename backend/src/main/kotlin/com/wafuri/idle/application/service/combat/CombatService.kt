package com.wafuri.idle.application.service.combat

import com.wafuri.idle.application.model.CombatSnapshot
import com.wafuri.idle.application.model.toSnapshot
import com.wafuri.idle.application.port.out.CombatStateRepository
import com.wafuri.idle.application.port.out.PlayerStateWorkQueue
import com.wafuri.idle.application.port.out.Repository
import com.wafuri.idle.application.service.enemy.EnemyTemplateCatalog
import com.wafuri.idle.application.service.player.ProgressionService
import com.wafuri.idle.application.service.scaling.ScalingRule
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
  private val enemyTemplateCatalog: EnemyTemplateCatalog,
  private val randomSource: RandomSource,
  private val progressionService: ProgressionService,
  private val scalingRule: ScalingRule,
) {
  @Transactional
  fun start(playerId: UUID): CombatSnapshot {
    playerRepository.require(playerId)
    val teamStats = combatStatService.teamStatsForPlayer(playerId)
    val teamSkills = combatStatService.teamSkillsForPlayerOrNull(playerId).orEmpty()
    val zone = zoneTemplateCatalog.default()
    val zoneLevel = progressionService.requireZoneProgress(playerId, zone.id).level
    val enemy = enemyTemplateCatalog.requireRandom(zone.enemies, randomSource)
    val currentState = combatStateRepository.findById(playerId) ?: CombatState.idle(playerId)
    val enemyAttack = scalingRule.enemyAttackFor(zoneLevel, enemy.attack)
    val startedState =
      currentState.start(
        zoneId = zone.id,
        teamId = teamStats.teamId,
        enemyId = enemy.id,
        enemyName = enemy.name,
        enemyImage = enemy.image,
        enemyLevel = zoneLevel,
        enemyBaseHp = enemy.baseHp,
        enemyAttack = enemyAttack,
        enemyMaxHp = scalingRule.enemyHpFor(zoneLevel, enemy.baseHp),
        members = teamStats.toCombatMembers(characterSkills = teamSkills),
      )
    val nextState = startedState.copy(lastSimulatedAt = Instant.now())
    val savedState = combatStateRepository.save(nextState)
    playerStateWorkQueue.markDirty(playerId)
    return savedState.toSnapshot()
  }

  @Transactional
  fun stop(playerId: UUID): CombatSnapshot? {
    playerRepository.require(playerId)
    val savedState = combatStateRepository.save(CombatState.idle(playerId))
    playerStateWorkQueue.markDirty(playerId)
    return savedState.takeUnless { it.status == com.wafuri.idle.domain.model.CombatStatus.IDLE }?.toSnapshot()
  }
}
