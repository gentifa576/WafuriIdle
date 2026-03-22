package com.wafuri.idle.domain.model

data class Item(
  val name: String,
  val displayName: String,
  val type: ItemType,
  val baseStat: Stat,
  val subStatPool: List<StatType>,
) {
  init {
    require(name.isNotBlank()) { "Item name must not be blank." }
    require(displayName.isNotBlank()) { "Item display name must not be blank." }
    require(subStatPool.isNotEmpty()) { "Item sub stat pool must not be empty." }
    require(subStatPool.distinct().size == subStatPool.size) { "Item sub stat pool must not contain duplicates." }
    require(baseStat.type !in subStatPool) { "Item base stat must not also appear in the sub stat pool." }
  }
}
