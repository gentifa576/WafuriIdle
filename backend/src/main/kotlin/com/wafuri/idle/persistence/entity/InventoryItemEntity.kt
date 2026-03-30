package com.wafuri.idle.persistence.entity

import com.wafuri.idle.domain.model.Rarity
import com.wafuri.idle.domain.model.Stat
import com.wafuri.idle.persistence.converter.StatListConverter
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "inventory_items")
class InventoryItemEntity {
  @Id
  @Column(nullable = false, updatable = false)
  lateinit var id: UUID

  @Column(nullable = false)
  lateinit var playerId: UUID

  @Column(nullable = false)
  lateinit var itemName: String

  @Column(nullable = false)
  var itemLevel: Int = 1

  @Convert(converter = StatListConverter::class)
  @Column(nullable = false)
  var subStats: List<Stat> = emptyList()

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  lateinit var rarity: Rarity

  @Column(nullable = false)
  var upgrade: Int = 0

  @Column
  var equippedTeamId: UUID? = null

  @Column
  var equippedPosition: Int? = null
}
