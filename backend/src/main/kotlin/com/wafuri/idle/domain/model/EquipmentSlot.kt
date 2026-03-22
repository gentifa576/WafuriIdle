package com.wafuri.idle.domain.model

enum class EquipmentSlot(
  val allowedType: ItemType,
) {
  WEAPON(ItemType.WEAPON),
  ARMOR(ItemType.ARMOR),
  ACCESSORY(ItemType.ACCESSORY),
}
