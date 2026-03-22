package com.wafuri.idle.persistence.entity

import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
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

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "team_characters", joinColumns = [JoinColumn(name = "team_id")])
  @Column(name = "character_key", nullable = false)
  var characterKeys: MutableList<String> = mutableListOf()
}
