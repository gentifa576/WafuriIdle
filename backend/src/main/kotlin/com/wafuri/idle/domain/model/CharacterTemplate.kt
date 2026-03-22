package com.wafuri.idle.domain.model

data class CharacterTemplate(
  val key: String,
  val name: String,
  val strength: StatGrowth,
  val agility: StatGrowth,
  val intelligence: StatGrowth,
  val wisdom: StatGrowth,
  val vitality: StatGrowth,
  val image: String? = null,
  val tags: List<String> = emptyList(),
  val skill: SkillDefinition? = null,
  val passive: PassiveDefinition? = null,
) {
  init {
    require(key.isNotBlank()) { "Character template key must not be blank." }
    require(name.isNotBlank()) { "Character template name must not be blank." }
  }
}
