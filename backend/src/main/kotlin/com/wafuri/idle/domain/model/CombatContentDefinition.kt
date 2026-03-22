package com.wafuri.idle.domain.model

data class SkillDefinition(
  val key: String,
  val name: String,
  val cooldownMillis: Long,
  val effects: List<CombatEffectDefinition> = emptyList(),
) {
  init {
    require(key.isNotBlank()) { "Skill key must not be blank." }
    require(name.isNotBlank()) { "Skill name must not be blank." }
    require(cooldownMillis >= 0L) { "Skill cooldown must not be negative." }
  }
}

data class PassiveDefinition(
  val key: String,
  val name: String,
  val leaderOnly: Boolean = true,
  val trigger: PassiveTriggerType = PassiveTriggerType.AURA,
  val triggerEvent: CombatEventType? = null,
  val triggerEveryCount: Int? = null,
  val condition: CombatConditionDefinition = CombatConditionDefinition(type = CombatConditionType.ALWAYS),
  val modifiers: List<CombatModifierDefinition> = emptyList(),
  val effects: List<CombatEffectDefinition> = emptyList(),
) {
  init {
    require(key.isNotBlank()) { "Passive key must not be blank." }
    require(name.isNotBlank()) { "Passive name must not be blank." }
    require(triggerEveryCount == null || triggerEveryCount > 0) {
      "Passive triggerEveryCount must be positive when provided."
    }
  }
}

data class CombatConditionDefinition(
  val type: CombatConditionType,
  val percent: Float? = null,
  val minimumCount: Int? = null,
  val tag: String? = null,
)

enum class CombatConditionType {
  ALWAYS,
  SELF_HP_BELOW_PERCENT,
  ANY_ALLY_HP_BELOW_PERCENT,
  ALIVE_ALLIES_WITH_TAG_AT_LEAST,
}

enum class PassiveTriggerType {
  AURA,
  EVENT_COUNTER,
}

enum class CombatEventType {
  HIT_DONE,
  DAMAGE_TAKEN,
  KILL_DONE,
}

data class CombatEffectDefinition(
  val type: CombatEffectType,
  val target: CombatTargetType,
  val amount: CombatValueFormula? = null,
  val durationMillis: Long? = null,
  val modifiers: List<CombatModifierDefinition> = emptyList(),
) {
  init {
    require(durationMillis == null || durationMillis > 0L) {
      "Combat effect duration must be positive when provided."
    }
  }
}

enum class CombatEffectType {
  DAMAGE,
  HEAL,
  APPLY_EFFECT,
  APPLY_DAMAGE_REDIRECT,
}

enum class CombatTargetType {
  SELF,
  ENEMY,
  TEAM,
  LOWEST_HP_ALLY,
}

data class CombatValueFormula(
  val type: CombatValueFormulaType,
  val value: Float? = null,
  val stat: CombatStatType? = null,
  val multiplier: Float? = null,
  val flatBonus: Float = 0f,
)

enum class CombatValueFormulaType {
  FLAT,
  STAT_SCALING,
  PERCENT_OF_SELF_CURRENT_HP,
  PERCENT_OF_TARGET_MAX_HP,
}

enum class CombatStatType {
  ATTACK,
  HIT,
  MAX_HP,
  CURRENT_HP,
}

data class CombatModifierDefinition(
  val type: CombatModifierType,
  val value: Float,
)

enum class CombatModifierType {
  ATTACK_MULTIPLIER,
  OUTGOING_DAMAGE_MULTIPLIER,
  INCOMING_DAMAGE_MULTIPLIER,
}
