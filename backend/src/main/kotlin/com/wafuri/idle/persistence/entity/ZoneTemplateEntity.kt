package com.wafuri.idle.persistence.entity

import com.wafuri.idle.domain.model.ZoneLootEntry
import com.wafuri.idle.persistence.converter.StringListConverter
import com.wafuri.idle.persistence.converter.ZoneLootEntryListConverter
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "zone_templates")
class ZoneTemplateEntity {
  @Id
  @Column(name = "zone_id", nullable = false, updatable = false)
  lateinit var id: String

  @Column(nullable = false)
  lateinit var name: String

  @Column(nullable = false)
  var minLevel: Int = 1

  @Column(nullable = false)
  var maxLevel: Int = 1

  @Convert(converter = StringListConverter::class)
  @Column(nullable = false)
  var eventRefs: List<String> = emptyList()

  @Convert(converter = ZoneLootEntryListConverter::class)
  @Column(nullable = false)
  var lootTable: List<ZoneLootEntry> = emptyList()

  @Convert(converter = StringListConverter::class)
  @Column(nullable = false)
  var enemies: List<String> = emptyList()
}
