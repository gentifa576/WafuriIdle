package com.wafuri.idle.domain.model

data class Stat(
  val type: StatType,
  val value: Float,
) {
  init {
    require(value >= 0f) { "Stat value must not be negative." }
  }
}

enum class StatType {
  STRENGTH,
  AGILITY,
  INTELLIGENCE,
  WISDOM,
  VITALITY,
  CRIT_CHANCE,
  CRIT_DAMAGE,
  SKILL_COOLDOWN,
}
