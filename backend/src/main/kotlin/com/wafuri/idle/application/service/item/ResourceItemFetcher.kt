package com.wafuri.idle.application.service.item

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.wafuri.idle.application.port.out.ItemFetcher
import com.wafuri.idle.domain.model.Item
import com.wafuri.idle.domain.model.ItemType
import com.wafuri.idle.domain.model.Stat
import com.wafuri.idle.domain.model.StatType
import io.quarkus.arc.profile.UnlessBuildProfile
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
@UnlessBuildProfile("prod")
class ResourceItemFetcher(
  private val objectMapper: ObjectMapper,
) : ItemFetcher {
  private val typeReference = object : TypeReference<List<ResourceItemRecord>>() {}

  override fun fetch(): List<Item> {
    val stream = javaClass.classLoader.getResourceAsStream(RESOURCE_PATH) ?: return emptyList()
    val records = stream.use { objectMapper.readValue(it, typeReference) }
    return records.map(::fromRecord)
  }

  companion object {
    private const val RESOURCE_PATH = "items/items.json"

    private fun fromRecord(record: ResourceItemRecord): Item =
      Item(record.name, record.displayName, record.type, record.baseStat, record.subStatPool)
  }
}

private data class ResourceItemRecord(
  val name: String,
  val displayName: String,
  val type: ItemType,
  val baseStat: Stat,
  val subStatPool: List<StatType>,
)
