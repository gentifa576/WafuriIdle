package com.wafuri.idle.transport.rest.dto

import com.wafuri.idle.domain.model.EquipmentSlot
import java.util.UUID

data class CreatePlayerRequest(
  val name: String,
)

data class EquipItemRequest(
  val inventoryItemId: UUID,
  val slot: EquipmentSlot,
)

data class UnequipItemRequest(
  val slot: EquipmentSlot,
)
