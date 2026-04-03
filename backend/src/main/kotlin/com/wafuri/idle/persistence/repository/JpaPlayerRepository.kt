package com.wafuri.idle.persistence.repository

import com.wafuri.idle.application.port.out.Repository
import com.wafuri.idle.domain.model.Player
import com.wafuri.idle.persistence.entity.PlayerEntity
import jakarta.inject.Singleton
import jakarta.persistence.EntityManager
import java.util.UUID

@Singleton
class JpaPlayerRepository(
  private val entityManager: EntityManager,
) : AbstractJpaRepository<Player, PlayerEntity, UUID>(entityManager, PlayerEntity::class.java),
  Repository<Player, UUID> {
  override val resourceName: String
    get() = "Player"

  override fun entityId(domain: Player): UUID = domain.id

  override fun toDomain(entity: PlayerEntity): Player = entity.toDomain()

  override fun toEntity(
    domain: Player,
    existing: PlayerEntity?,
  ): PlayerEntity =
    (existing ?: PlayerEntity()).also {
      it.id = domain.id
      it.name = domain.name
      it.experience = domain.experience
      it.level = domain.level
      it.gold = domain.gold
      it.essence = domain.essence
      it.ownedCharacterKeys = domain.ownedCharacterKeys.toMutableSet()
      it.activeTeamId = domain.activeTeamId
    }
}

private fun PlayerEntity.toDomain(): Player =
  Player(
    id,
    name,
    ownedCharacterKeys = ownedCharacterKeys.toSet(),
    activeTeamId = activeTeamId,
    experience = experience,
    level = level,
    gold = gold,
    essence = essence,
  )
