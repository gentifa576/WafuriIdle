package com.wafuri.idle.persistence.repository

import com.wafuri.idle.application.port.out.ZoneTemplateRepository
import com.wafuri.idle.domain.model.LevelRange
import com.wafuri.idle.domain.model.ZoneTemplate
import com.wafuri.idle.persistence.entity.ZoneTemplateEntity
import jakarta.inject.Singleton
import jakarta.persistence.EntityManager

@Singleton
class JpaZoneTemplateRepository(
  private val entityManager: EntityManager,
) : AbstractJpaRepository<ZoneTemplate, ZoneTemplateEntity, String>(
    entityManager,
    ZoneTemplateEntity::class.java,
  ),
  ZoneTemplateRepository {
  override fun entityId(domain: ZoneTemplate): String = domain.id

  override fun toDomain(entity: ZoneTemplateEntity): ZoneTemplate = entity.toDomain()

  override fun toEntity(
    domain: ZoneTemplate,
    existing: ZoneTemplateEntity?,
  ): ZoneTemplateEntity =
    (existing ?: ZoneTemplateEntity()).also {
      it.id = domain.id
      it.name = domain.name
      it.minLevel = domain.levelRange.min
      it.maxLevel = domain.levelRange.max
      it.eventRefs = domain.eventRefs
      it.lootTable = domain.lootTable
      it.enemies = domain.enemies
    }

  override fun findAll(): List<ZoneTemplate> =
    entityManager
      .createQuery(
        "from ZoneTemplateEntity order by minLevel, id",
        ZoneTemplateEntity::class.java,
      ).resultList
      .map { it.toDomain() }
}

private fun ZoneTemplateEntity.toDomain(): ZoneTemplate =
  ZoneTemplate(
    id,
    name,
    LevelRange(minLevel, maxLevel),
    eventRefs,
    lootTable,
    enemies,
  )
