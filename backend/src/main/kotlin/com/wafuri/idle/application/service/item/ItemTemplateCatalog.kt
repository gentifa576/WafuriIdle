package com.wafuri.idle.application.service.item

import com.wafuri.idle.application.exception.ResourceNotFoundException
import com.wafuri.idle.domain.model.Item
import jakarta.enterprise.context.ApplicationScoped
import java.util.concurrent.atomic.AtomicReference

@ApplicationScoped
class ItemTemplateCatalog {
  private val items = AtomicReference<Map<String, Item>>(emptyMap())

  fun replace(newItems: List<Item>) {
    require(newItems.map { it.name }.distinct().size == newItems.size) {
      "Item names must be unique."
    }
    items.set(newItems.associateBy { it.name })
  }

  fun all(): List<Item> = items.get().values.sortedBy { it.name }

  fun find(itemName: String): Item? = items.get()[itemName]

  fun require(itemName: String): Item =
    find(itemName)
      ?: throw ResourceNotFoundException("Item template $itemName was not found.")
}
