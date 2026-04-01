package com.wafuri.idle.persistence.repository

import com.wafuri.idle.application.port.out.AuthAccountRepository
import com.wafuri.idle.domain.model.AuthAccount
import com.wafuri.idle.persistence.entity.AuthAccountEntity
import jakarta.inject.Singleton
import jakarta.persistence.EntityManager
import java.util.UUID

@Singleton
class JpaAuthAccountRepository(
  private val entityManager: EntityManager,
) : AbstractJpaRepository<AuthAccount, AuthAccountEntity, UUID>(entityManager, AuthAccountEntity::class.java),
  AuthAccountRepository {
  override fun entityId(domain: AuthAccount): UUID = domain.playerId

  override fun toDomain(entity: AuthAccountEntity): AuthAccount = entity.toDomain()

  override fun toEntity(
    domain: AuthAccount,
    existing: AuthAccountEntity?,
  ): AuthAccountEntity =
    (existing ?: AuthAccountEntity()).also {
      it.playerId = domain.playerId
      it.username = domain.username
      it.email = domain.email
      it.passwordHash = domain.passwordHash
      it.passwordSalt = domain.passwordSalt
      it.role = domain.role
    }

  override fun findByUsername(username: String): AuthAccount? =
    entityManager
      .createQuery("select a from AuthAccountEntity a where a.username = :username", AuthAccountEntity::class.java)
      .setParameter("username", username)
      .resultList
      .firstOrNull()
      ?.toDomain()

  override fun findByEmail(email: String): AuthAccount? =
    entityManager
      .createQuery("select a from AuthAccountEntity a where a.email = :email", AuthAccountEntity::class.java)
      .setParameter("email", email)
      .resultList
      .firstOrNull()
      ?.toDomain()
}

private fun AuthAccountEntity.toDomain(): AuthAccount =
  AuthAccount(
    playerId,
    username,
    email,
    passwordHash,
    passwordSalt,
    role,
  )
