package com.wafuri.idle.domain.model

data class ZoneTemplate(
  val id: String,
  val name: String,
  val levelRange: LevelRange,
  val eventRefs: List<String>,
  val lootTable: List<ZoneLootEntry>,
  val enemies: List<String>,
) {
  init {
    require(id.isNotBlank()) { "Zone id must not be blank." }
    require(name.isNotBlank()) { "Zone name must not be blank." }
    require(enemies.isNotEmpty()) { "Zone must define at least one enemy." }
    require(enemies.none { it.isBlank() }) { "Zone enemy ids must not be blank." }
    require(eventRefs.none { it.isBlank() }) { "Zone event refs must not be blank." }
    require(lootTable.map { it.itemName }.distinct().size == lootTable.size) {
      "Zone loot entries must be unique by item name."
    }
  }
}

data class LevelRange(
  val min: Int,
  val max: Int,
) {
  init {
    require(min >= 1) { "Zone minimum level must be at least 1." }
    require(max >= min) { "Zone maximum level must be greater than or equal to minimum level." }
  }
}

data class ZoneLootEntry(
  val itemName: String,
  val weight: Int,
) {
  init {
    require(itemName.isNotBlank()) { "Zone loot item name must not be blank." }
    require(weight > 0) { "Zone loot weight must be positive." }
  }
}
