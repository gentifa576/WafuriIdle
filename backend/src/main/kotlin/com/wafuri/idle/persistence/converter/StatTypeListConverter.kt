package com.wafuri.idle.persistence.converter

import com.wafuri.idle.domain.model.StatType
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

@Converter
class StatTypeListConverter : AttributeConverter<List<StatType>, String> {
  override fun convertToDatabaseColumn(attribute: List<StatType>?): String = attribute.orEmpty().joinToString(SEPARATOR) { it.name }

  override fun convertToEntityAttribute(dbData: String?): List<StatType> =
    dbData
      .orEmpty()
      .split(SEPARATOR)
      .filter { it.isNotBlank() }
      .map(StatType::valueOf)

  companion object {
    private const val SEPARATOR = ","
  }
}
