package com.wafuri.idle.persistence.repository

import com.wafuri.idle.application.port.out.InventoryRepository
import com.wafuri.idle.application.service.item.ItemTemplateCatalog
import com.wafuri.idle.domain.model.EquipmentSlot
import com.wafuri.idle.domain.model.InventoryItem
import com.wafuri.idle.domain.model.Item
import com.wafuri.idle.persistence.entity.InventoryItemEntity
import jakarta.inject.Singleton
import jakarta.persistence.EntityManager
import java.util.UUID

@Singleton
class JpaInventoryRepository(
  private val entityManager: EntityManager,
  private val itemTemplateCatalog: ItemTemplateCatalog,
) : AbstractJpaRepository<InventoryItem, InventoryItemEntity, UUID>(entityManager, InventoryItemEntity::class.java),
  InventoryRepository {
  override fun entityId(domain: InventoryItem): UUID = domain.id

  override fun toDomain(entity: InventoryItemEntity): InventoryItem = entity.toDomain(itemTemplateCatalog.require(entity.itemName))

  override fun toEntity(
    domain: InventoryItem,
    existing: InventoryItemEntity?,
  ): InventoryItemEntity =
    (existing ?: InventoryItemEntity()).also {
      it.id = domain.id
      it.playerId = domain.playerId
      it.itemName = domain.item.name
      it.subStats = domain.subStats
      it.rarity = domain.rarity
      it.upgrade = domain.upgrade
      it.equippedCharacterKey = domain.equippedCharacterKey
    }

  override fun findByPlayerId(playerId: UUID): List<InventoryItem> =
    entityManager
      .createQuery(
        "from InventoryItemEntity where playerId = :playerId",
        InventoryItemEntity::class.java,
      ).setParameter("playerId", playerId)
      .resultList
      .map(::toDomain)

  override fun findByCharacterAndSlot(
    characterKey: String,
    slot: EquipmentSlot,
  ): InventoryItem? =
    entityManager
      .createQuery(
        "from InventoryItemEntity where equippedCharacterKey = :characterKey",
        InventoryItemEntity::class.java,
      ).setParameter("characterKey", characterKey)
      .resultList
      .firstOrNull()
      ?.let(::toDomain)
      ?.takeIf { inventoryItem -> inventoryItem.item.type == slot.allowedType }
}

private fun InventoryItemEntity.toDomain(item: Item): InventoryItem =
  InventoryItem(
    id = id,
    playerId = playerId,
    item = item,
    subStats = subStats,
    rarity = rarity,
    upgrade = upgrade,
    equippedCharacterKey = equippedCharacterKey,
  )
