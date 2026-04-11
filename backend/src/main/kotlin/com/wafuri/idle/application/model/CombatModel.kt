package com.wafuri.idle.application.model

import com.wafuri.idle.domain.model.CombatEffectDefinition
import com.wafuri.idle.domain.model.CombatMemberState
import com.wafuri.idle.domain.model.CombatSkillDefinition
import com.wafuri.idle.domain.model.CombatSkillState
import com.wafuri.idle.domain.model.CombatState
import com.wafuri.idle.domain.model.CombatStatus
import java.util.UUID

data class CharacterCombatStats(
  val characterKey: String,
  val attack: Float,
  val hit: Float,
  val maxHp: Float,
)

data class CharacterCombatSkillStats(
  val key: String,
  val cooldownMillis: Long,
  val effects: List<CombatEffectDefinition> = emptyList(),
)

data class TeamCombatStats(
  val teamId: UUID,
  val characterStats: List<CharacterCombatStats>,
) {
  val dps: Float = characterStats.sumOf { (it.attack * it.hit).toDouble() }.toFloat()

  fun toCombatMembers(
    existingMembers: List<CombatMemberState> = emptyList(),
    characterSkills: Map<String, CharacterCombatSkillStats> = emptyMap(),
  ): List<CombatMemberState> {
    val existingByCharacterKey = existingMembers.associateBy { it.characterKey }
    return characterStats.map { stats ->
      val existing = existingByCharacterKey[stats.characterKey]
      CombatMemberState(
        characterKey = stats.characterKey,
        attack = stats.attack,
        hit = stats.hit,
        currentHp = existing?.preserveHpRatio(stats.maxHp) ?: stats.maxHp,
        maxHp = stats.maxHp,
        skill =
          characterSkills[stats.characterKey]?.let { skill ->
            val existingSkill = existing?.skill
            CombatSkillState(
              cooldownMillis = skill.cooldownMillis,
              remainingMillis =
                existingSkill
                  ?.remainingMillis
                  ?.coerceAtMost(skill.cooldownMillis)
                  ?: 0L,
            )
          },
      )
    }
  }
}

fun Map<String, CharacterCombatSkillStats>.toCombatSkillDefinitions(): Map<String, CombatSkillDefinition> =
  mapValues { (_, skill) -> CombatSkillDefinition(effects = skill.effects) }

private fun CombatMemberState.preserveHpRatio(nextMaxHp: Float): Float {
  if (maxHp <= 0f) {
    return nextMaxHp
  }

  val hpRatio = (currentHp / maxHp).coerceIn(0f, 1f)
  return nextMaxHp * hpRatio
}

data class CombatSnapshot(
  val playerId: UUID,
  val status: CombatStatus,
  val zoneId: String?,
  val activeTeamId: UUID?,
  val enemyName: String?,
  val enemyImage: String? = null,
  val enemyAttack: Float,
  val enemyHp: Float,
  val enemyMaxHp: Float,
  val teamDps: Float,
  val pendingReviveMillis: Long,
  val members: List<CombatMemberSnapshot>,
)

data class CombatMemberSnapshot(
  val characterKey: String,
  val attack: Float,
  val hit: Float,
  val currentHp: Float,
  val maxHp: Float,
  val alive: Boolean,
  val skillCooldownRemainingMillis: Long? = null,
)

fun CombatState.toSnapshot(): CombatSnapshot =
  CombatSnapshot(
    playerId = playerId,
    status = status,
    zoneId = zoneId,
    activeTeamId = activeTeamId,
    enemyName = enemyName,
    enemyImage = enemyImage,
    enemyAttack = enemyAttack,
    enemyHp = enemyHp,
    enemyMaxHp = enemyMaxHp,
    teamDps = teamDps,
    pendingReviveMillis = pendingReviveMillis,
    members =
      members.map { member ->
        CombatMemberSnapshot(
          characterKey = member.characterKey,
          attack = member.attack,
          hit = member.hit,
          currentHp = member.currentHp,
          maxHp = member.maxHp,
          alive = member.isAlive,
          skillCooldownRemainingMillis = member.skill?.remainingMillis?.takeIf { it > 0L },
        )
      },
  )
