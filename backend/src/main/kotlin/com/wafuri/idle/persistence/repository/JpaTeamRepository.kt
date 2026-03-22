package com.wafuri.idle.persistence.repository

import com.wafuri.idle.application.port.out.TeamRepository
import com.wafuri.idle.domain.model.Team
import com.wafuri.idle.persistence.entity.TeamEntity
import jakarta.inject.Singleton
import jakarta.persistence.EntityManager
import java.util.UUID

@Singleton
class JpaTeamRepository(
  private val entityManager: EntityManager,
) : AbstractJpaRepository<Team, TeamEntity, UUID>(entityManager, TeamEntity::class.java),
  TeamRepository {
  override fun entityId(domain: Team): UUID = domain.id

  override fun toDomain(entity: TeamEntity): Team = entity.toDomain()

  override fun toEntity(
    domain: Team,
    existing: TeamEntity?,
  ): TeamEntity =
    (existing ?: TeamEntity()).also {
      it.id = domain.id
      it.playerId = domain.playerId
      it.characterKeys = domain.characterKeys.toMutableList()
    }

  override fun findByPlayerId(playerId: UUID): List<Team> =
    entityManager
      .createQuery(
        "from TeamEntity where playerId = :playerId",
        TeamEntity::class.java,
      ).setParameter("playerId", playerId)
      .resultList
      .map { it.toDomain() }
}

private fun TeamEntity.toDomain(): Team = Team(id = id, playerId = playerId, characterKeys = characterKeys.toList())
