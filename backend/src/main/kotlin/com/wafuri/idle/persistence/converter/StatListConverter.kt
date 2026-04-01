package com.wafuri.idle.persistence.converter

import com.wafuri.idle.domain.model.Stat
import com.wafuri.idle.domain.model.StatType
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

@Converter
class StatListConverter : AttributeConverter<List<Stat>, String> {
  override fun convertToDatabaseColumn(attribute: List<Stat>?): String =
    attribute.orEmpty().joinToString(SEPARATOR) { stat -> "${stat.type.name}$KEY_VALUE_SEPARATOR${stat.value}" }

  override fun convertToEntityAttribute(dbData: String?): List<Stat> =
    dbData
      .orEmpty()
      .split(SEPARATOR)
      .filter { it.isNotBlank() }
      .map { entry ->
        val parts = entry.split(KEY_VALUE_SEPARATOR, limit = 2)
        require(parts.size == 2) { "Inventory sub stat must be stored as STAT:value." }
        Stat(StatType.valueOf(parts[0]), parts[1].toFloat())
      }

  companion object {
    private const val SEPARATOR = ","
    private const val KEY_VALUE_SEPARATOR = ":"
  }
}
