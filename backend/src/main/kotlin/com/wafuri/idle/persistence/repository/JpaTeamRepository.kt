package com.wafuri.idle.persistence.repository

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.wafuri.idle.application.port.out.TeamRepository
import com.wafuri.idle.domain.model.Team
import com.wafuri.idle.domain.model.TeamMemberSlot
import com.wafuri.idle.persistence.entity.TeamEntity
import jakarta.inject.Singleton
import jakarta.persistence.EntityManager
import java.util.UUID

@Singleton
class JpaTeamRepository(
  private val entityManager: EntityManager,
  private val objectMapper: ObjectMapper,
) : AbstractJpaRepository<Team, TeamEntity, UUID>(entityManager, TeamEntity::class.java),
  TeamRepository {
  override fun entityId(domain: Team): UUID = domain.id

  override fun toDomain(entity: TeamEntity): Team =
    Team(
      entity.id,
      entity.playerId,
      objectMapper.readValue(entity.slotsJson, object : TypeReference<List<TeamMemberSlot>>() {}),
    )

  override fun toEntity(
    domain: Team,
    existing: TeamEntity?,
  ): TeamEntity =
    (existing ?: TeamEntity()).also {
      it.id = domain.id
      it.playerId = domain.playerId
      it.slotsJson = objectMapper.writeValueAsString(domain.slots)
    }

  override fun findByPlayerId(playerId: UUID): List<Team> =
    entityManager
      .createQuery(
        "from TeamEntity where playerId = :playerId",
        TeamEntity::class.java,
      ).setParameter("playerId", playerId)
      .resultList
      .map(::toDomain)
}
