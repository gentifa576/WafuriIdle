package com.wafuri.idle.persistence.repository

import com.wafuri.idle.application.port.out.RevokedSessionRepository
import com.wafuri.idle.domain.model.RevokedSession
import com.wafuri.idle.persistence.entity.RevokedSessionEntity
import jakarta.inject.Singleton
import jakarta.persistence.EntityManager
import java.util.UUID

@Singleton
class JpaRevokedSessionRepository(
  entityManager: EntityManager,
) : AbstractJpaRepository<RevokedSession, RevokedSessionEntity, UUID>(entityManager, RevokedSessionEntity::class.java),
  RevokedSessionRepository {
  override fun entityId(domain: RevokedSession): UUID = domain.sessionId

  override fun toDomain(entity: RevokedSessionEntity): RevokedSession = RevokedSession(entity.sessionId, entity.expiresAt)

  override fun toEntity(
    domain: RevokedSession,
    existing: RevokedSessionEntity?,
  ): RevokedSessionEntity =
    (existing ?: RevokedSessionEntity()).also {
      it.sessionId = domain.sessionId
      it.expiresAt = domain.expiresAt
    }

  override fun delete(sessionId: UUID) {
    entityManager()
      .createQuery("delete from RevokedSessionEntity r where r.sessionId = :sessionId")
      .setParameter("sessionId", sessionId)
      .executeUpdate()
  }
}
