package com.wafuri.idle.domain.model

import java.time.Instant
import java.util.UUID

data class CombatState(
  val playerId: UUID,
  val status: CombatStatus = CombatStatus.IDLE,
  val zoneId: String? = null,
  val activeTeamId: UUID? = null,
  val enemyName: String? = null,
  val enemyHp: Float = 0f,
  val enemyMaxHp: Float = 0f,
  val members: List<CombatMemberState> = emptyList(),
  val pendingDamageMillis: Long = 0,
  val pendingRespawnMillis: Long = 0,
  val lastSimulatedAt: Instant? = null,
) {
  init {
    require(enemyHp >= 0f) { "Enemy HP must not be negative." }
    require(enemyMaxHp >= 0f) { "Enemy max HP must not be negative." }
    require(pendingDamageMillis >= 0) { "Pending damage millis must not be negative." }
    require(pendingRespawnMillis >= 0) { "Pending respawn millis must not be negative." }
    require(members.map { it.characterKey }.distinct().size == members.size) { "Combat members must be unique." }

    if (status == CombatStatus.IDLE) {
      require(zoneId == null) { "Idle combat must not have a zone." }
      require(activeTeamId == null) { "Idle combat must not have an active team." }
      require(enemyName == null) { "Idle combat must not have an enemy." }
      require(enemyHp == 0f) { "Idle combat must not have enemy HP." }
      require(enemyMaxHp == 0f) { "Idle combat must not have enemy max HP." }
      require(members.isEmpty()) { "Idle combat must not have combat members." }
      require(pendingDamageMillis == 0L) { "Idle combat must not have pending damage time." }
      require(pendingRespawnMillis == 0L) { "Idle combat must not have pending respawn time." }
    } else {
      require(!zoneId.isNullOrBlank()) { "Active combat must have a zone." }
      require(activeTeamId != null) { "Active combat must have an active team." }
      require(!enemyName.isNullOrBlank()) { "Active combat must have an enemy name." }
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
    enemyMaxHp: Float,
    members: List<CombatMemberState>,
  ): CombatState {
    require(zoneId.isNotBlank()) { "Zone id must not be blank." }
    require(enemyName.isNotBlank()) { "Enemy name must not be blank." }
    require(enemyMaxHp > 0f) { "Enemy max HP must be positive." }
    require(members.isNotEmpty()) { "Combat must start with at least one member." }
    require(members.any { it.isAlive }) { "Combat must start with at least one living member." }

    return copy(
      status = CombatStatus.FIGHTING,
      zoneId = zoneId,
      activeTeamId = teamId,
      enemyName = enemyName,
      enemyHp = enemyMaxHp,
      enemyMaxHp = enemyMaxHp,
      members = members,
      pendingDamageMillis = 0L,
      pendingRespawnMillis = 0L,
    )
  }

  fun refreshMembers(members: List<CombatMemberState>): CombatState {
    return refreshTeam(activeTeamId ?: return this, members)
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
  ): CombatState {
    require(elapsedMillis >= 0L) { "Combat elapsed millis must not be negative." }
    require(damageIntervalMillis > 0L) { "Combat damage interval must be positive." }
    require(respawnDelayMillis >= 0L) { "Combat respawn delay must not be negative." }
    if (status != CombatStatus.FIGHTING) {
      if (status != CombatStatus.WON) {
        return this
      }
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
        )
      return restarted.advance(
        elapsedMillis = overflowMillis,
        damageIntervalMillis = damageIntervalMillis,
        respawnDelayMillis = respawnDelayMillis,
      )
    }

    val totalPendingMillis = pendingDamageMillis + elapsedMillis
    val damageSteps = totalPendingMillis / damageIntervalMillis
    if (damageSteps == 0L) {
      return copy(pendingDamageMillis = totalPendingMillis)
    }

    val intervalSeconds = damageIntervalMillis / 1000f
    val damage = teamDps * intervalSeconds * damageSteps
    val nextEnemyHp = (enemyHp - damage).coerceAtLeast(0f)
    val nextStatus = if (nextEnemyHp == 0f) CombatStatus.WON else CombatStatus.FIGHTING
    return copy(
      enemyHp = nextEnemyHp,
      status = nextStatus,
      pendingDamageMillis = totalPendingMillis % damageIntervalMillis,
      pendingRespawnMillis = 0L,
    )
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
}

enum class CombatStatus {
  IDLE,
  FIGHTING,
  WON,
}
