package com.wafuri.idle.domain.model

import java.time.Instant
import java.util.UUID

data class CombatState(
  val playerId: UUID,
  val status: CombatStatus = CombatStatus.IDLE,
  val zoneId: String? = null,
  val activeTeamId: UUID? = null,
  val enemyId: String? = null,
  val enemyName: String? = null,
  val enemyImage: String? = null,
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
  companion object {
    fun idle(
      playerId: UUID,
      lastSimulatedAt: Instant? = null,
    ): CombatState =
      CombatState(
        playerId = playerId,
        lastSimulatedAt = lastSimulatedAt,
      )

    fun active(
      playerId: UUID,
      status: CombatStatus,
      zoneId: String,
      activeTeamId: UUID,
      enemyId: String,
      enemyName: String,
      enemyImage: String? = null,
      enemyLevel: Int,
      enemyBaseHp: Float,
      enemyAttack: Float,
      enemyHp: Float,
      enemyMaxHp: Float,
      members: List<CombatMemberState>,
      pendingDamageMillis: Long = 0L,
      pendingRespawnMillis: Long = 0L,
      pendingReviveMillis: Long = 0L,
      lastSimulatedAt: Instant? = null,
    ): CombatState {
      require(status != CombatStatus.IDLE) { "Active combat state must not use idle status." }
      return CombatState(
        playerId = playerId,
        status = status,
        zoneId = zoneId,
        activeTeamId = activeTeamId,
        enemyId = enemyId,
        enemyName = enemyName,
        enemyImage = enemyImage,
        enemyLevel = enemyLevel,
        enemyBaseHp = enemyBaseHp,
        enemyAttack = enemyAttack,
        enemyHp = enemyHp,
        enemyMaxHp = enemyMaxHp,
        members = members,
        pendingDamageMillis = pendingDamageMillis,
        pendingRespawnMillis = pendingRespawnMillis,
        pendingReviveMillis = pendingReviveMillis,
        lastSimulatedAt = lastSimulatedAt,
      )
    }
  }

  init {
    require(enemyHp >= 0f) { "Enemy HP must not be negative." }
    require(enemyBaseHp >= 0f) { "Enemy base HP must not be negative." }
    require(enemyAttack >= 0f) { "Enemy attack must not be negative." }
    require(enemyMaxHp >= 0f) { "Enemy max HP must not be negative." }
    require(enemyImage == null || enemyImage.isNotBlank()) { "Enemy image must not be blank when provided." }
    require(pendingDamageMillis >= 0) { "Pending damage millis must not be negative." }
    require(pendingRespawnMillis >= 0) { "Pending respawn millis must not be negative." }
    require(pendingReviveMillis >= 0) { "Pending revive millis must not be negative." }
    require(members.map { it.characterKey }.distinct().size == members.size) { "Combat members must be unique." }

    if (status == CombatStatus.IDLE) {
      require(zoneId == null) { "Idle combat must not have a zone." }
      require(activeTeamId == null) { "Idle combat must not have an active team." }
      require(enemyId == null) { "Idle combat must not have an enemy id." }
      require(enemyName == null) { "Idle combat must not have an enemy." }
      require(enemyImage == null) { "Idle combat must not have an enemy image." }
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
      require(!enemyId.isNullOrBlank()) { "Active combat must have an enemy id." }
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
    enemyId: String,
    enemyName: String,
    enemyImage: String? = null,
    enemyLevel: Int,
    enemyBaseHp: Float,
    enemyAttack: Float,
    enemyMaxHp: Float,
    members: List<CombatMemberState>,
  ): CombatState {
    require(zoneId.isNotBlank()) { "Zone id must not be blank." }
    require(enemyId.isNotBlank()) { "Enemy id must not be blank." }
    require(enemyName.isNotBlank()) { "Enemy name must not be blank." }
    require(enemyLevel >= 1) { "Enemy level must be at least 1." }
    require(enemyBaseHp > 0f) { "Enemy base HP must be positive." }
    require(enemyAttack > 0f) { "Enemy attack must be positive." }
    require(enemyMaxHp > 0f) { "Enemy max HP must be positive." }
    require(members.isNotEmpty()) { "Combat must start with at least one member." }
    require(members.any { it.isAlive }) { "Combat must start with at least one living member." }

    return active(
      playerId = playerId,
      status = CombatStatus.FIGHTING,
      zoneId = zoneId,
      activeTeamId = teamId,
      enemyId = enemyId,
      enemyName = enemyName,
      enemyImage = enemyImage,
      enemyLevel = enemyLevel,
      enemyBaseHp = enemyBaseHp,
      enemyAttack = enemyAttack,
      enemyHp = enemyMaxHp,
      enemyMaxHp = enemyMaxHp,
      members = members,
    )
  }

  fun refreshEnemy(
    enemyId: String,
    enemyName: String,
    enemyImage: String? = null,
    enemyBaseHp: Float,
    enemyLevel: Int,
    enemyAttack: Float,
    enemyMaxHp: Float,
  ): CombatState {
    if (status != CombatStatus.FIGHTING) {
      return this
    }
    require(enemyId.isNotBlank()) { "Enemy id must not be blank." }
    require(enemyName.isNotBlank()) { "Enemy name must not be blank." }
    require(enemyBaseHp > 0f) { "Enemy base HP must be positive." }
    require(enemyLevel >= 1) { "Enemy level must be at least 1." }
    require(enemyAttack > 0f) { "Enemy attack must be positive." }
    require(enemyMaxHp > 0f) { "Enemy max HP must be positive." }
    return copy(
      enemyId = enemyId,
      enemyName = enemyName,
      enemyImage = enemyImage,
      enemyBaseHp = enemyBaseHp,
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
    skillDefinitions: Map<String, CombatSkillDefinition> = emptyMap(),
    skillEvents: MutableList<CombatSkillDamageEvent>? = null,
  ): CombatState {
    require(elapsedMillis >= 0L) { "Combat elapsed millis must not be negative." }
    require(damageIntervalMillis > 0L) { "Combat damage interval must be positive." }
    require(respawnDelayMillis >= 0L) { "Combat respawn delay must not be negative." }
    require(reviveDelayMillis >= 0L) { "Combat revive delay must not be negative." }
    require(reviveHpRatio in 0f..1f) { "Combat revive HP ratio must be within 0 and 1." }
    val totalPendingMillis = pendingDamageMillis + elapsedMillis
    if (status != CombatStatus.FIGHTING) {
      if (status == CombatStatus.WON) {
        val cooledMembers = tickSkillCooldowns(members, elapsedMillis)
        val totalPendingRespawnMillis = pendingRespawnMillis + elapsedMillis
        if (totalPendingRespawnMillis < respawnDelayMillis) {
          return copy(
            members = cooledMembers,
            pendingRespawnMillis = totalPendingRespawnMillis,
          )
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
            members = cooledMembers,
          )
        return restarted.advance(
          overflowMillis,
          damageIntervalMillis,
          respawnDelayMillis,
          reviveDelayMillis,
          reviveHpRatio,
          skillDefinitions,
          skillEvents,
        )
      }
      if (status != CombatStatus.DOWN) {
        return this
      }
      val cooledMembers = tickSkillCooldowns(members, elapsedMillis)
      val totalPendingReviveMillis = pendingReviveMillis + elapsedMillis
      if (totalPendingReviveMillis < reviveDelayMillis) {
        return copy(
          members = cooledMembers,
          pendingReviveMillis = totalPendingReviveMillis,
        )
      }

      val overflowMillis = totalPendingReviveMillis - reviveDelayMillis
      val revived =
        copy(
          status = CombatStatus.FIGHTING,
          zoneId = zoneId,
          enemyHp = enemyMaxHp,
          members = cooledMembers.map { it.revive(reviveHpRatio) },
          pendingDamageMillis = 0L,
          pendingRespawnMillis = 0L,
          pendingReviveMillis = 0L,
        )
      return revived.advance(
        overflowMillis,
        damageIntervalMillis,
        respawnDelayMillis,
        reviveDelayMillis,
        reviveHpRatio,
        skillDefinitions,
        skillEvents,
      )
    }

    val firstStepDelayMillis =
      if (pendingDamageMillis == 0L) {
        damageIntervalMillis
      } else {
        damageIntervalMillis - pendingDamageMillis
      }
    if (elapsedMillis < firstStepDelayMillis) {
      return copy(
        members = tickSkillCooldowns(members, elapsedMillis),
        pendingDamageMillis = totalPendingMillis,
      )
    }

    val intervalSeconds = damageIntervalMillis / 1000f
    var nextEnemyHp = enemyHp
    var nextMembers = members
    var consumedElapsedMillis = firstStepDelayMillis
    var statusAfterStep = status
    nextMembers = tickSkillCooldowns(nextMembers, firstStepDelayMillis)

    while (statusAfterStep == CombatStatus.FIGHTING) {
      val resolved = resolveDamageStep(nextMembers, nextEnemyHp, intervalSeconds, skillDefinitions)
      nextEnemyHp = resolved.enemyHp
      nextMembers = resolved.members
      if (skillEvents != null) {
        skillEvents.addAll(resolved.skillEvents)
      }
      statusAfterStep =
        when {
          nextMembers.none { it.isAlive } -> CombatStatus.DOWN
          nextEnemyHp == 0f -> CombatStatus.WON
          else -> CombatStatus.FIGHTING
        }
      if (statusAfterStep != CombatStatus.FIGHTING) {
        break
      }
      if (consumedElapsedMillis + damageIntervalMillis > elapsedMillis) {
        break
      }
      nextMembers = tickSkillCooldowns(nextMembers, damageIntervalMillis)
      consumedElapsedMillis += damageIntervalMillis
    }

    val trailingElapsedMillis = (elapsedMillis - consumedElapsedMillis).coerceAtLeast(0L)
    if (trailingElapsedMillis > 0L) {
      nextMembers = tickSkillCooldowns(nextMembers, trailingElapsedMillis)
    }

    return copy(
      enemyHp = nextEnemyHp,
      status = statusAfterStep,
      members = nextMembers,
      pendingDamageMillis = totalPendingMillis % damageIntervalMillis,
      pendingRespawnMillis = if (statusAfterStep == CombatStatus.WON) 0L else pendingRespawnMillis,
      pendingReviveMillis = if (statusAfterStep == CombatStatus.DOWN) 0L else pendingReviveMillis,
    )
  }

  private fun resolveDamageStep(
    currentMembers: List<CombatMemberState>,
    currentEnemyHp: Float,
    intervalSeconds: Float,
    skillDefinitions: Map<String, CombatSkillDefinition>,
  ): StepResolution {
    val membersByCharacterKey =
      currentMembers
        .associateBy { it.characterKey }
        .toMutableMap()
    val livingMembers = currentMembers.filter { it.isAlive }
    val skillEvents = mutableListOf<CombatSkillDamageEvent>()
    val baseDamage = livingMembers.sumOf { (it.attack * it.hit).toDouble() }.toFloat() * intervalSeconds
    val skillDamage =
      livingMembers
        .asReversed()
        .sumOf { member ->
          val memberState = membersByCharacterKey[member.characterKey] ?: return@sumOf 0.0
          val skill = memberState.skill ?: return@sumOf 0.0
          if (!skill.isReady()) {
            return@sumOf 0.0
          }
          membersByCharacterKey[member.characterKey] = memberState.copy(skill = skill.consume())
          val skillDefinition = skillDefinitions[member.characterKey] ?: CombatSkillDefinition()
          val damage = skillDamageFor(memberState, skillDefinition, enemyMaxHp, currentEnemyHp)
          damage.takeIf { it > 0f }?.let {
            skillEvents.add(
              CombatSkillDamageEvent(
                characterKey = member.characterKey,
                damage = it,
              ),
            )
          }
          damage.toDouble()
        }.toFloat()
    val updatedMembers = currentMembers.map { member -> membersByCharacterKey.getValue(member.characterKey) }
    val totalPlayerDamage = baseDamage + skillDamage
    val nextEnemyHp = (currentEnemyHp - totalPlayerDamage).coerceAtLeast(0f)
    val nextMembers =
      if (nextEnemyHp > 0f && enemyAttack > 0f) {
        applyRetaliationDamageAllMembers(updatedMembers, enemyAttack)
      } else {
        updatedMembers
      }
    return StepResolution(
      enemyHp = nextEnemyHp,
      members = nextMembers,
      skillEvents = skillEvents,
    )
  }

  private fun applyRetaliationDamageAllMembers(
    currentMembers: List<CombatMemberState>,
    damagePerMember: Float,
  ): List<CombatMemberState> {
    if (damagePerMember <= 0f) {
      return currentMembers
    }

    return currentMembers.map { member ->
      if (!member.isAlive) {
        member
      } else {
        member.copy(currentHp = (member.currentHp - damagePerMember).coerceAtLeast(0f))
      }
    }
  }

  fun maxPotentialDamagePerStep(skillDefinitions: Map<String, CombatSkillDefinition> = emptyMap()): Float {
    val baseDamagePerStep = teamDps
    val maxSkillDamage =
      members
        .sumOf { member ->
          if (member.skill == null) {
            0.0
          } else {
            val skillDefinition = skillDefinitions[member.characterKey] ?: CombatSkillDefinition()
            skillDamageFor(member, skillDefinition, enemyMaxHp, enemyHp).toDouble()
          }
        }.toFloat()
    return baseDamagePerStep + maxSkillDamage
  }

  private fun skillDamageFor(
    member: CombatMemberState,
    definition: CombatSkillDefinition,
    enemyMaxHp: Float,
    enemyCurrentHp: Float,
  ): Float =
    definition.effects
      .filter { it.type == CombatEffectType.DAMAGE && it.target == CombatTargetType.ENEMY }
      .sumOf { effect -> effectDamage(effect, member, enemyMaxHp, enemyCurrentHp).toDouble() }
      .toFloat()

  private fun effectDamage(
    effect: CombatEffectDefinition,
    member: CombatMemberState,
    enemyMaxHp: Float,
    enemyCurrentHp: Float,
  ): Float {
    val amount = effect.amount ?: return 0f
    return when (amount.type) {
      CombatValueFormulaType.FLAT -> (amount.value ?: 0f) + amount.flatBonus
      CombatValueFormulaType.STAT_SCALING -> {
        val scaledStat =
          when (amount.stat) {
            CombatStatType.ATTACK -> member.attack
            CombatStatType.HIT -> member.hit
            CombatStatType.MAX_HP -> member.maxHp
            CombatStatType.CURRENT_HP -> member.currentHp
            null -> 0f
          }
        (scaledStat * (amount.multiplier ?: 0f)) + amount.flatBonus
      }
      CombatValueFormulaType.PERCENT_OF_SELF_CURRENT_HP ->
        (member.currentHp * ((amount.value ?: 0f) / 100f)) + amount.flatBonus
      CombatValueFormulaType.PERCENT_OF_TARGET_MAX_HP ->
        (enemyMaxHp * ((amount.value ?: 0f) / 100f)) + amount.flatBonus
    }.coerceAtMost(enemyCurrentHp).coerceAtLeast(0f)
  }

  private fun tickSkillCooldowns(
    currentMembers: List<CombatMemberState>,
    elapsedMillis: Long,
  ): List<CombatMemberState> {
    if (elapsedMillis <= 0L || currentMembers.isEmpty()) {
      return currentMembers
    }
    return currentMembers.map { member ->
      if (member.skill == null) {
        member
      } else {
        member.copy(skill = member.skill.tick(elapsedMillis))
      }
    }
  }

  private data class StepResolution(
    val enemyHp: Float,
    val members: List<CombatMemberState>,
    val skillEvents: List<CombatSkillDamageEvent> = emptyList(),
  )
}

data class CombatSkillDamageEvent(
  val characterKey: String,
  val damage: Float,
) {
  init {
    require(characterKey.isNotBlank()) { "Combat skill event character key must not be blank." }
    require(damage >= 0f) { "Combat skill event damage must not be negative." }
  }
}

data class CombatMemberState(
  val characterKey: String,
  val attack: Float,
  val hit: Float,
  val currentHp: Float,
  val maxHp: Float,
  val skill: CombatSkillState? = null,
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

data class CombatSkillState(
  val cooldownMillis: Long,
  val remainingMillis: Long = 0L,
) {
  init {
    require(cooldownMillis >= 0L) { "Combat skill cooldown must not be negative." }
    require(remainingMillis >= 0L) { "Combat skill remaining cooldown must not be negative." }
  }

  fun isReady(): Boolean = remainingMillis <= 0L

  fun consume(): CombatSkillState = copy(remainingMillis = cooldownMillis)

  fun tick(elapsedMillis: Long): CombatSkillState =
    if (remainingMillis <= 0L || elapsedMillis <= 0L) {
      this
    } else {
      copy(remainingMillis = (remainingMillis - elapsedMillis).coerceAtLeast(0L))
    }
}

data class CombatSkillDefinition(
  val effects: List<CombatEffectDefinition> = emptyList(),
)
