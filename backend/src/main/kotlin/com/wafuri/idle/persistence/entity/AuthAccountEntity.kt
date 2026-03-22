package com.wafuri.idle.persistence.entity

import com.wafuri.idle.domain.model.AuthScope
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "auth_accounts")
class AuthAccountEntity {
  @Id
  @Column(nullable = false, updatable = false)
  lateinit var playerId: UUID

  @Column(nullable = false, unique = true)
  lateinit var username: String

  @Column(unique = true)
  var email: String? = null

  @Column(nullable = false)
  lateinit var passwordHash: String

  @Column(nullable = false)
  lateinit var passwordSalt: String

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  lateinit var role: AuthScope
}
