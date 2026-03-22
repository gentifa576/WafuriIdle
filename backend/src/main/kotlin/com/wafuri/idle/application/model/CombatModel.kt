package com.wafuri.idle.application.model

import com.wafuri.idle.domain.model.CombatMemberState
import com.wafuri.idle.domain.model.CombatState
import com.wafuri.idle.domain.model.CombatStatus
import java.util.UUID

data class CharacterCombatStats(
  val characterKey: String,
  val attack: Float,
  val hit: Float,
  val maxHp: Float,
)

data class TeamCombatStats(
  val teamId: UUID,
  val characterStats: List<CharacterCombatStats>,
) {
  val dps: Float = characterStats.sumOf { (it.attack * it.hit).toDouble() }.toFloat()

  fun toCombatMembers(existingMembers: List<CombatMemberState> = emptyList()): List<CombatMemberState> {
    val existingByCharacterKey = existingMembers.associateBy { it.characterKey }
    return characterStats.map { stats ->
      val existing = existingByCharacterKey[stats.characterKey]
      CombatMemberState(
        characterKey = stats.characterKey,
        attack = stats.attack,
        hit = stats.hit,
        currentHp = existing?.currentHp?.coerceAtMost(stats.maxHp) ?: stats.maxHp,
        maxHp = stats.maxHp,
      )
    }
  }
}

data class CombatSnapshot(
  val playerId: UUID,
  val status: CombatStatus,
  val zoneId: String?,
  val activeTeamId: UUID?,
  val enemyName: String?,
  val enemyHp: Float,
  val enemyMaxHp: Float,
  val teamDps: Float,
  val members: List<CombatMemberSnapshot>,
)

data class CombatMemberSnapshot(
  val characterKey: String,
  val attack: Float,
  val hit: Float,
  val currentHp: Float,
  val maxHp: Float,
  val alive: Boolean,
)

fun CombatState.toSnapshot(): CombatSnapshot =
  CombatSnapshot(
    playerId = playerId,
    status = status,
    zoneId = zoneId,
    activeTeamId = activeTeamId,
    enemyName = enemyName,
    enemyHp = enemyHp,
    enemyMaxHp = enemyMaxHp,
    teamDps = teamDps,
    members =
      members.map { member ->
        CombatMemberSnapshot(
          characterKey = member.characterKey,
          attack = member.attack,
          hit = member.hit,
          currentHp = member.currentHp,
          maxHp = member.maxHp,
          alive = member.isAlive,
        )
      },
  )
