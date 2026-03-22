package com.wafuri.idle.persistence.repository

import com.wafuri.idle.application.port.out.ItemTemplateRepository
import com.wafuri.idle.domain.model.Item
import com.wafuri.idle.persistence.entity.ItemEntity
import jakarta.inject.Singleton
import jakarta.persistence.EntityManager

@Singleton
class JpaItemRepository(
  private val entityManager: EntityManager,
) : AbstractJpaRepository<Item, ItemEntity, String>(entityManager, ItemEntity::class.java),
  ItemTemplateRepository {
  override fun entityId(domain: Item): String = domain.name

  override fun toDomain(entity: ItemEntity): Item = entity.toDomain()

  override fun toEntity(
    domain: Item,
    existing: ItemEntity?,
  ): ItemEntity =
    (existing ?: ItemEntity()).also {
      it.name = domain.name
      it.displayName = domain.displayName
      it.type = domain.type
      it.baseStat = domain.baseStat
      it.subStatPool = domain.subStatPool
    }

  override fun findAll(): List<Item> =
    entityManager
      .createQuery("from ItemEntity", ItemEntity::class.java)
      .resultList
      .map { it.toDomain() }
}

private fun ItemEntity.toDomain(): Item =
  Item(
    name = name,
    displayName = displayName,
    type = type,
    baseStat = baseStat,
    subStatPool = subStatPool,
  )
