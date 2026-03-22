package com.wafuri.idle.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "teams")
class TeamEntity {
  @Id
  @Column(nullable = false, updatable = false)
  lateinit var id: UUID

  @Column(nullable = false)
  lateinit var playerId: UUID

  @Column(name = "slots_json", nullable = false, columnDefinition = "text")
  lateinit var slotsJson: String
}
