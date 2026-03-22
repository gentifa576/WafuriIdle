package com.wafuri.idle.persistence.converter

import com.wafuri.idle.domain.model.ZoneLootEntry
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

@Converter
class ZoneLootEntryListConverter : AttributeConverter<List<ZoneLootEntry>, String> {
  override fun convertToDatabaseColumn(attribute: List<ZoneLootEntry>?): String =
    attribute.orEmpty().joinToString(ENTRY_SEPARATOR) { entry -> "${entry.itemName}$KEY_VALUE_SEPARATOR${entry.weight}" }

  override fun convertToEntityAttribute(dbData: String?): List<ZoneLootEntry> =
    dbData
      .orEmpty()
      .split(ENTRY_SEPARATOR)
      .filter { it.isNotBlank() }
      .map { entry ->
        val parts = entry.split(KEY_VALUE_SEPARATOR, limit = 2)
        require(parts.size == 2) { "Zone loot entry must be stored as itemName:weight." }
        ZoneLootEntry(itemName = parts[0], weight = parts[1].toInt())
      }

  companion object {
    private const val ENTRY_SEPARATOR = ";"
    private const val KEY_VALUE_SEPARATOR = ":"
  }
}
