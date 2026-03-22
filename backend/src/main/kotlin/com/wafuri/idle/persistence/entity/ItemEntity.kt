package com.wafuri.idle.persistence.entity

import com.wafuri.idle.domain.model.ItemType
import com.wafuri.idle.domain.model.Stat
import com.wafuri.idle.domain.model.StatType
import com.wafuri.idle.persistence.converter.StatConverter
import com.wafuri.idle.persistence.converter.StatTypeListConverter
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "items")
class ItemEntity {
  @Id
  @Column(nullable = false, updatable = false)
  lateinit var name: String

  @Column(nullable = false)
  lateinit var displayName: String

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  lateinit var type: ItemType

  @Convert(converter = StatConverter::class)
  @Column(nullable = false)
  lateinit var baseStat: Stat

  @Convert(converter = StatTypeListConverter::class)
  @Column(nullable = false)
  var subStatPool: List<StatType> = emptyList()
}
