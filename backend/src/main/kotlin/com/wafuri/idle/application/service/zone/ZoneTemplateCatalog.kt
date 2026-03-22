package com.wafuri.idle.application.service.zone

import com.wafuri.idle.application.exception.ResourceNotFoundException
import com.wafuri.idle.domain.model.ZoneTemplate
import jakarta.enterprise.context.ApplicationScoped
import java.util.concurrent.atomic.AtomicReference

@ApplicationScoped
class ZoneTemplateCatalog {
  private val zones = AtomicReference<Map<String, ZoneTemplate>>(emptyMap())

  fun replace(newZones: List<ZoneTemplate>) {
    require(newZones.map { it.id }.distinct().size == newZones.size) {
      "Zone ids must be unique."
    }
    zones.set(newZones.associateBy { it.id })
  }

  fun all(): List<ZoneTemplate> =
    zones
      .get()
      .values
      .sortedWith(compareBy<ZoneTemplate> { it.levelRange.min }.thenBy { it.id })

  fun find(zoneId: String): ZoneTemplate? = zones.get()[zoneId]

  fun require(zoneId: String): ZoneTemplate =
    find(zoneId)
      ?: throw ResourceNotFoundException("Zone template $zoneId was not found.")

  fun default(): ZoneTemplate =
    all().firstOrNull()
      ?: throw ResourceNotFoundException("No zone templates are available.")
}
