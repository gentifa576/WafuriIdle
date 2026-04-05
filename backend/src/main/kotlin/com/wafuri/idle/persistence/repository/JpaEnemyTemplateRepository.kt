package com.wafuri.idle.persistence.repository

import com.wafuri.idle.application.port.out.EnemyTemplateRepository
import com.wafuri.idle.domain.model.EnemyTemplate
import com.wafuri.idle.persistence.entity.EnemyTemplateEntity
import jakarta.inject.Singleton
import jakarta.persistence.EntityManager

@Singleton
class JpaEnemyTemplateRepository(
  private val entityManager: EntityManager,
) : AbstractJpaRepository<EnemyTemplate, EnemyTemplateEntity, String>(
    entityManager,
    EnemyTemplateEntity::class.java,
  ),
  EnemyTemplateRepository {
  override fun entityId(domain: EnemyTemplate): String = domain.id

  override fun toDomain(entity: EnemyTemplateEntity): EnemyTemplate =
    EnemyTemplate(
      id = entity.id,
      name = entity.name,
      image = entity.image,
      baseHp = entity.baseHp,
      attack = entity.attack,
    )

  override fun toEntity(
    domain: EnemyTemplate,
    existing: EnemyTemplateEntity?,
  ): EnemyTemplateEntity =
    (existing ?: EnemyTemplateEntity()).also {
      it.id = domain.id
      it.name = domain.name
      it.image = domain.image
      it.baseHp = domain.baseHp
      it.attack = domain.attack
    }

  override fun findAll(): List<EnemyTemplate> =
    entityManager
      .createQuery(
        "from EnemyTemplateEntity order by name",
        EnemyTemplateEntity::class.java,
      ).resultList
      .map(::toDomain)
}
