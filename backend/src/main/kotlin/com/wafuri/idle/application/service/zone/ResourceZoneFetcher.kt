package com.wafuri.idle.application.service.zone

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.wafuri.idle.application.port.out.ZoneFetcher
import com.wafuri.idle.domain.model.LevelRange
import com.wafuri.idle.domain.model.ZoneLootEntry
import com.wafuri.idle.domain.model.ZoneTemplate
import io.quarkus.arc.profile.UnlessBuildProfile
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
@UnlessBuildProfile("prod")
class ResourceZoneFetcher(
  private val objectMapper: ObjectMapper,
) : ZoneFetcher {
  override fun fetch(): List<ZoneTemplate> {
    val stream = javaClass.classLoader.getResourceAsStream(RESOURCE_PATH) ?: return emptyList()
    return stream.use { input ->
      objectMapper
        .readValue(input, object : TypeReference<List<ResourceZoneTemplateRecord>>() {})
        .map { record ->
          ZoneTemplate(
            id = record.id,
            name = record.name,
            levelRange = LevelRange(min = record.levelRange.first, max = record.levelRange.last),
            eventRefs = emptyList(),
            lootTable =
              record.lootTable.map { loot ->
                ZoneLootEntry(itemName = loot.itemName, weight = loot.weight)
              },
            enemies = record.enemies,
          )
        }
    }
  }

  companion object {
    private const val RESOURCE_PATH = "zones/zones.json"
  }
}

private data class ResourceZoneTemplateRecord(
  val id: String,
  val name: String,
  val levelRange: ResourceLevelRangeRecord,
  val lootTable: List<ResourceZoneLootEntryRecord>,
  val enemies: List<String>,
)

private data class ResourceLevelRangeRecord(
  val first: Int,
  val last: Int,
)

private data class ResourceZoneLootEntryRecord(
  val itemName: String,
  val weight: Int,
)
