package com.wafuri.idle.persistence.repository

import com.wafuri.idle.application.port.out.PlayerZoneProgressRepository
import com.wafuri.idle.domain.model.PlayerZoneProgress
import com.wafuri.idle.persistence.entity.PlayerZoneProgressEntity
import com.wafuri.idle.persistence.entity.PlayerZoneProgressEntityId
import jakarta.inject.Singleton
import jakarta.persistence.EntityManager
import java.util.UUID

@Singleton
class JpaPlayerZoneProgressRepository(
  private val entityManager: EntityManager,
) : PlayerZoneProgressRepository {
  override fun save(domain: PlayerZoneProgress): PlayerZoneProgress {
    val entityId = domain.toEntityId()
    val existing = entityManager.find(PlayerZoneProgressEntity::class.java, entityId)
    val entity =
      (existing ?: PlayerZoneProgressEntity()).also {
        it.id = entityId
        it.killCount = domain.killCount
        it.level = domain.level
      }
    if (existing == null) {
      entityManager.persist(entity)
    }
    return entity.toDomain()
  }

  override fun findByPlayerIdAndZoneId(
    playerId: UUID,
    zoneId: String,
  ): PlayerZoneProgress? {
    val entityId = PlayerZoneProgressEntityId(playerId, zoneId)
    return entityManager.find(PlayerZoneProgressEntity::class.java, entityId)?.toDomain()
  }

  override fun findByPlayerId(playerId: UUID): List<PlayerZoneProgress> =
    entityManager
      .createQuery(
        "from PlayerZoneProgressEntity where id.playerId = :playerId order by id.zoneId",
        PlayerZoneProgressEntity::class.java,
      ).setParameter("playerId", playerId)
      .resultList
      .map { it.toDomain() }
}

private fun PlayerZoneProgressEntity.toDomain(): PlayerZoneProgress =
  PlayerZoneProgress(
    requireNotNull(id.playerId),
    requireNotNull(id.zoneId),
    killCount,
    level,
  )

private fun PlayerZoneProgress.toEntityId(): PlayerZoneProgressEntityId = PlayerZoneProgressEntityId(playerId, zoneId)
