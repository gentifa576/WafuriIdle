package com.wafuri.idle.persistence.repository

import com.wafuri.idle.application.port.out.CharacterTemplateRepository
import com.wafuri.idle.domain.model.CharacterTemplate
import com.wafuri.idle.domain.model.StatGrowth
import com.wafuri.idle.persistence.entity.CharacterTemplateEntity
import jakarta.inject.Singleton
import jakarta.persistence.EntityManager

@Singleton
class JpaCharacterTemplateRepository(
  private val entityManager: EntityManager,
) : AbstractJpaRepository<CharacterTemplate, CharacterTemplateEntity, String>(
    entityManager,
    CharacterTemplateEntity::class.java,
  ),
  CharacterTemplateRepository {
  override fun entityId(domain: CharacterTemplate): String = domain.key

  override fun toDomain(entity: CharacterTemplateEntity): CharacterTemplate = entity.toDomain()

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
      it.skillRefs = domain.skillRefs
      it.passiveRef = domain.passiveRef
    }

  override fun findAll(): List<CharacterTemplate> =
    entityManager
      .createQuery(
        "from CharacterTemplateEntity order by name",
        CharacterTemplateEntity::class.java,
      ).resultList
      .map { it.toDomain() }
}

private fun CharacterTemplateEntity.toDomain(): CharacterTemplate =
  CharacterTemplate(
    key = key,
    name = name,
    strength = StatGrowth(base = strengthBase, increment = strengthIncrement),
    agility = StatGrowth(base = agilityBase, increment = agilityIncrement),
    intelligence = StatGrowth(base = intelligenceBase, increment = intelligenceIncrement),
    wisdom = StatGrowth(base = wisdomBase, increment = wisdomIncrement),
    vitality = StatGrowth(base = vitalityBase, increment = vitalityIncrement),
    image = image,
    skillRefs = skillRefs,
    passiveRef = passiveRef,
  )
