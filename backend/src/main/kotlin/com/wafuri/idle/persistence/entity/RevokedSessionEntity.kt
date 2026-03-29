package com.wafuri.idle.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "revoked_sessions")
class RevokedSessionEntity {
  @Id
  @Column(nullable = false, updatable = false)
  lateinit var sessionId: UUID

  @Column(nullable = false)
  lateinit var expiresAt: Instant
}
