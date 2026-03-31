package com.wafuri.idle.domain.model

import java.time.Instant
import java.util.UUID

data class CombatState(
  val playerId: UUID,
  val status: CombatStatus = CombatStatus.IDLE,
  val zoneId: String? = null,
  val activeTeamId: UUID? = null,
  val enemyName: String? = null,
  val enemyLevel: Int = 0,
  val enemyBaseHp: Float = 0f,
  val enemyAttack: Float = 0f,
  val enemyHp: Float = 0f,
  val enemyMaxHp: Float = 0f,
  val members: List<CombatMemberState> = emptyList(),
  val pendingDamageMillis: Long = 0,
  val pendingRespawnMillis: Long = 0,
  val pendingReviveMillis: Long = 0,
  val lastSimulatedAt: Instant? = null,
) {
  init {
    require(enemyHp >= 0f) { "Enemy HP must not be negative." }
    require(enemyBaseHp >= 0f) { "Enemy base HP must not be negative." }
    require(enemyAttack >= 0f) { "Enemy attack must not be negative." }
    require(enemyMaxHp >= 0f) { "Enemy max HP must not be negative." }
    require(pendingDamageMillis >= 0) { "Pending damage millis must not be negative." }
    require(pendingRespawnMillis >= 0) { "Pending respawn millis must not be negative." }
    require(pendingReviveMillis >= 0) { "Pending revive millis must not be negative." }
    require(members.map { it.characterKey }.distinct().size == members.size) { "Combat members must be unique." }

    if (status == CombatStatus.IDLE) {
      require(zoneId == null) { "Idle combat must not have a zone." }
      require(activeTeamId == null) { "Idle combat must not have an active team." }
      require(enemyName == null) { "Idle combat must not have an enemy." }
      require(enemyLevel == 0) { "Idle combat must not have an enemy level." }
      require(enemyBaseHp == 0f) { "Idle combat must not have an enemy base HP." }
      require(enemyAttack == 0f) { "Idle combat must not have an enemy attack." }
      require(enemyHp == 0f) { "Idle combat must not have enemy HP." }
      require(enemyMaxHp == 0f) { "Idle combat must not have enemy max HP." }
      require(members.isEmpty()) { "Idle combat must not have combat members." }
      require(pendingDamageMillis == 0L) { "Idle combat must not have pending damage time." }
      require(pendingRespawnMillis == 0L) { "Idle combat must not have pending respawn time." }
      require(pendingReviveMillis == 0L) { "Idle combat must not have pending revive time." }
    } else {
      require(!zoneId.isNullOrBlank()) { "Active combat must have a zone." }
      require(activeTeamId != null) { "Active combat must have an active team." }
      require(!enemyName.isNullOrBlank()) { "Active combat must have an enemy name." }
      require(enemyLevel >= 1) { "Active combat must have an enemy level." }
      require(enemyBaseHp > 0f) { "Active combat must have positive enemy base HP." }
      require(enemyAttack > 0f) { "Active combat must have positive enemy attack." }
      require(enemyMaxHp > 0f) { "Active combat must have positive enemy max HP." }
      require(enemyHp <= enemyMaxHp) { "Enemy HP must not exceed enemy max HP." }
      require(members.isNotEmpty()) { "Active combat must have at least one combat member." }
    }
  }

  val teamDps: Float
    get() = members.sumOf { it.dps.toDouble() }.toFloat()

  val teamHp: Float
    get() = members.sumOf { it.currentHp.toDouble() }.toFloat()

  fun start(
    zoneId: String,
    teamId: UUID,
    enemyName: String,
    enemyLevel: Int,
    enemyBaseHp: Float,
    enemyAttack: Float,
    enemyMaxHp: Float,
    members: List<CombatMemberState>,
  ): CombatState {
    require(zoneId.isNotBlank()) { "Zone id must not be blank." }
    require(enemyName.isNotBlank()) { "Enemy name must not be blank." }
    require(enemyLevel >= 1) { "Enemy level must be at least 1." }
    require(enemyBaseHp > 0f) { "Enemy base HP must be positive." }
    require(enemyAttack > 0f) { "Enemy attack must be positive." }
    require(enemyMaxHp > 0f) { "Enemy max HP must be positive." }
    require(members.isNotEmpty()) { "Combat must start with at least one member." }
    require(members.any { it.isAlive }) { "Combat must start with at least one living member." }

    return copy(
      status = CombatStatus.FIGHTING,
      zoneId = zoneId,
      activeTeamId = teamId,
      enemyName = enemyName,
      enemyLevel = enemyLevel,
      enemyBaseHp = enemyBaseHp,
      enemyAttack = enemyAttack,
      enemyHp = enemyMaxHp,
      enemyMaxHp = enemyMaxHp,
      members = members,
      pendingDamageMillis = 0L,
      pendingRespawnMillis = 0L,
      pendingReviveMillis = 0L,
    )
  }

  fun refreshMembers(members: List<CombatMemberState>): CombatState {
    return refreshTeam(activeTeamId ?: return this, members)
  }

  fun refreshEnemy(
    enemyLevel: Int,
    enemyAttack: Float,
    enemyMaxHp: Float,
  ): CombatState {
    if (status != CombatStatus.FIGHTING) {
      return this
    }
    require(enemyLevel >= 1) { "Enemy level must be at least 1." }
    require(enemyAttack > 0f) { "Enemy attack must be positive." }
    require(enemyMaxHp > 0f) { "Enemy max HP must be positive." }
    return copy(
      enemyLevel = enemyLevel,
      enemyAttack = enemyAttack,
      enemyHp = enemyMaxHp,
      enemyMaxHp = enemyMaxHp,
    )
  }

  fun refreshTeam(
    teamId: UUID,
    members: List<CombatMemberState>,
  ): CombatState {
    if (status == CombatStatus.IDLE) {
      return this
    }
    require(members.isNotEmpty()) { "Active combat must keep at least one member." }
    return copy(activeTeamId = teamId, members = members)
  }

  fun advance(
    elapsedMillis: Long,
    damageIntervalMillis: Long,
    respawnDelayMillis: Long,
    reviveDelayMillis: Long,
    reviveHpRatio: Float,
  ): CombatState {
    require(elapsedMillis >= 0L) { "Combat elapsed millis must not be negative." }
    require(damageIntervalMillis > 0L) { "Combat damage interval must be positive." }
    require(respawnDelayMillis >= 0L) { "Combat respawn delay must not be negative." }
    require(reviveDelayMillis >= 0L) { "Combat revive delay must not be negative." }
    require(reviveHpRatio in 0f..1f) { "Combat revive HP ratio must be within 0 and 1." }
    if (status != CombatStatus.FIGHTING) {
      if (status == CombatStatus.WON) {
        val totalPendingRespawnMillis = pendingRespawnMillis + elapsedMillis
        if (totalPendingRespawnMillis < respawnDelayMillis) {
          return copy(pendingRespawnMillis = totalPendingRespawnMillis)
        }

        val overflowMillis = totalPendingRespawnMillis - respawnDelayMillis
        val restarted =
          copy(
            status = CombatStatus.FIGHTING,
            zoneId = zoneId,
            enemyHp = enemyMaxHp,
            pendingDamageMillis = 0L,
            pendingRespawnMillis = 0L,
            pendingReviveMillis = 0L,
          )
        return restarted.advance(overflowMillis, damageIntervalMillis, respawnDelayMillis, reviveDelayMillis, reviveHpRatio)
      }
      if (status != CombatStatus.DOWN) {
        return this
      }
      val totalPendingReviveMillis = pendingReviveMillis + elapsedMillis
      if (totalPendingReviveMillis < reviveDelayMillis) {
        return copy(pendingReviveMillis = totalPendingReviveMillis)
      }

      val overflowMillis = totalPendingReviveMillis - reviveDelayMillis
      val revived =
        copy(
          status = CombatStatus.FIGHTING,
          zoneId = zoneId,
          enemyHp = enemyMaxHp,
          members = members.map { it.revive(reviveHpRatio) },
          pendingDamageMillis = 0L,
          pendingRespawnMillis = 0L,
          pendingReviveMillis = 0L,
        )
      return revived.advance(overflowMillis, damageIntervalMillis, respawnDelayMillis, reviveDelayMillis, reviveHpRatio)
    }

    val totalPendingMillis = pendingDamageMillis + elapsedMillis
    val damageSteps = totalPendingMillis / damageIntervalMillis
    if (damageSteps == 0L) {
      return copy(pendingDamageMillis = totalPendingMillis)
    }

    val intervalSeconds = damageIntervalMillis / 1000f
    val playerDamage = teamDps * intervalSeconds * damageSteps
    val nextEnemyHp = (enemyHp - playerDamage).coerceAtLeast(0f)
    val retaliationDamage = enemyAttack * damageSteps
    val updatedMembers = applyRetaliationDamage(retaliationDamage)
    val nextStatus =
      when {
        updatedMembers.none { it.isAlive } -> CombatStatus.DOWN
        nextEnemyHp == 0f -> CombatStatus.WON
        else -> CombatStatus.FIGHTING
      }
    return copy(
      enemyHp = nextEnemyHp,
      status = nextStatus,
      members = updatedMembers,
      pendingDamageMillis = totalPendingMillis % damageIntervalMillis,
      pendingRespawnMillis = if (nextStatus == CombatStatus.WON) 0L else pendingRespawnMillis,
      pendingReviveMillis = if (nextStatus == CombatStatus.DOWN) 0L else pendingReviveMillis,
    )
  }

  private fun applyRetaliationDamage(damage: Float): List<CombatMemberState> {
    if (damage <= 0f) {
      return members
    }

    var remainingDamage = damage
    return members.map { member ->
      if (!member.isAlive || remainingDamage <= 0f) {
        member
      } else {
        val appliedDamage = remainingDamage.coerceAtMost(member.currentHp)
        remainingDamage -= appliedDamage
        member.copy(currentHp = member.currentHp - appliedDamage)
      }
    }
  }
}

data class CombatMemberState(
  val characterKey: String,
  val attack: Float,
  val hit: Float,
  val currentHp: Float,
  val maxHp: Float,
) {
  init {
    require(characterKey.isNotBlank()) { "Combat member character key must not be blank." }
    require(attack >= 0f) { "Combat member attack must not be negative." }
    require(hit >= 0f) { "Combat member hit must not be negative." }
    require(currentHp >= 0f) { "Combat member current HP must not be negative." }
    require(maxHp >= 0f) { "Combat member max HP must not be negative." }
    require(currentHp <= maxHp) { "Combat member current HP must not exceed max HP." }
  }

  val isAlive: Boolean
    get() = currentHp > 0f

  val dps: Float
    get() = if (isAlive) attack * hit else 0f

  fun revive(hpRatio: Float): CombatMemberState = copy(currentHp = maxHp * hpRatio)
}

enum class CombatStatus {
  IDLE,
  FIGHTING,
  WON,
  DOWN,
}
