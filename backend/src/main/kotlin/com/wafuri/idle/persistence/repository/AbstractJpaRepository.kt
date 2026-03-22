package com.wafuri.idle.persistence.repository

import com.wafuri.idle.application.port.out.Repository
import jakarta.persistence.EntityManager

abstract class AbstractJpaRepository<D, E : Any, ID>(
  private val entityManager: EntityManager,
  private val entityClass: Class<E>,
) : Repository<D, ID> {
  protected fun entityManager(): EntityManager = entityManager

  protected abstract fun entityId(domain: D): ID

  protected abstract fun toDomain(entity: E): D

  protected abstract fun toEntity(
    domain: D,
    existing: E?,
  ): E

  override fun save(domain: D): D {
    val existing = entityManager.find(entityClass, entityId(domain))
    val entity = toEntity(domain, existing)
    if (existing == null) {
      entityManager.persist(entity)
    }
    return toDomain(entity)
  }

  override fun findById(id: ID): D? = entityManager.find(entityClass, id)?.let(::toDomain)
}
