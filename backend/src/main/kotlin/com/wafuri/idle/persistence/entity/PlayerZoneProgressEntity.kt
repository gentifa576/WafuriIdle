package com.wafuri.idle.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.io.Serializable
import java.util.UUID

@Entity
@Table(name = "player_zone_progress")
class PlayerZoneProgressEntity {
  @EmbeddedId
  lateinit var id: PlayerZoneProgressEntityId

  @Column(nullable = false)
  var killCount: Int = 0

  @Column(nullable = false)
  var level: Int = 1
}

@Embeddable
data class PlayerZoneProgressEntityId(
  @Column(nullable = false, updatable = false)
  var playerId: UUID? = null,
  @Column(nullable = false, updatable = false)
  var zoneId: String? = null,
) : Serializable
