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
@Table(name = "players")
class PlayerEntity {
  @Id
  @Column(nullable = false, updatable = false)
  lateinit var id: UUID

  @Column(nullable = false)
  lateinit var name: String

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "player_owned_characters", joinColumns = [JoinColumn(name = "player_id")])
  @Column(name = "character_key", nullable = false)
  var ownedCharacterKeys: MutableSet<String> = linkedSetOf()

  @Column
  var activeTeamId: UUID? = null
}
