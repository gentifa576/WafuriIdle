package com.wafuri.idle.persistence.repository

import com.fasterxml.jackson.databind.ObjectMapper
import com.wafuri.idle.application.port.out.CharacterTemplateRepository
import com.wafuri.idle.domain.model.CharacterTemplate
import com.wafuri.idle.domain.model.PassiveDefinition
import com.wafuri.idle.domain.model.SkillDefinition
import com.wafuri.idle.domain.model.StatGrowth
import com.wafuri.idle.persistence.entity.CharacterTemplateEntity
import jakarta.inject.Singleton
import jakarta.persistence.EntityManager

@Singleton
class JpaCharacterTemplateRepository(
  private val entityManager: EntityManager,
  private val objectMapper: ObjectMapper,
) : AbstractJpaRepository<CharacterTemplate, CharacterTemplateEntity, String>(
    entityManager,
    CharacterTemplateEntity::class.java,
  ),
  CharacterTemplateRepository {
  override fun entityId(domain: CharacterTemplate): String = domain.key

  override fun toDomain(entity: CharacterTemplateEntity): CharacterTemplate =
    CharacterTemplate(
      key = entity.key,
      name = entity.name,
      strength = StatGrowth(base = entity.strengthBase, increment = entity.strengthIncrement),
      agility = StatGrowth(base = entity.agilityBase, increment = entity.agilityIncrement),
      intelligence = StatGrowth(base = entity.intelligenceBase, increment = entity.intelligenceIncrement),
      wisdom = StatGrowth(base = entity.wisdomBase, increment = entity.wisdomIncrement),
      vitality = StatGrowth(base = entity.vitalityBase, increment = entity.vitalityIncrement),
      image = entity.image,
      tags = entity.tags,
      skill = entity.skillDefinition?.let { objectMapper.readValue(it, SkillDefinition::class.java) },
      passive = entity.passiveDefinition?.let { objectMapper.readValue(it, PassiveDefinition::class.java) },
    )

  override fun toEntity(
    domain: CharacterTemplate,
    existing: CharacterTemplateEntity?,
  ): CharacterTemplateEntity =
    (existing ?: CharacterTemplateEntity()).also {
      it.key = domain.key
      it.name = domain.name
      it.strengthBase = domain.strength.base
      it.strengthIncrement = domain.strength.increment
      it.agilityBase = domain.agility.base
      it.agilityIncrement = domain.agility.increment
      it.intelligenceBase = domain.intelligence.base
      it.intelligenceIncrement = domain.intelligence.increment
      it.wisdomBase = domain.wisdom.base
      it.wisdomIncrement = domain.wisdom.increment
      it.vitalityBase = domain.vitality.base
      it.vitalityIncrement = domain.vitality.increment
      it.image = domain.image
      it.tags = domain.tags
      it.skillDefinition = domain.skill?.let(objectMapper::writeValueAsString)
      it.passiveDefinition = domain.passive?.let(objectMapper::writeValueAsString)
    }

  override fun findAll(): List<CharacterTemplate> =
    entityManager
      .createQuery(
        "from CharacterTemplateEntity order by name",
        CharacterTemplateEntity::class.java,
      ).resultList
      .map { toDomain(it) }
}
