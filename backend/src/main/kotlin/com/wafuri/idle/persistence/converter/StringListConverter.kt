package com.wafuri.idle.persistence.converter

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

@Converter
class StringListConverter : AttributeConverter<List<String>, String> {
  override fun convertToDatabaseColumn(attribute: List<String>?): String = attribute.orEmpty().joinToString(SEPARATOR)

  override fun convertToEntityAttribute(dbData: String?): List<String> =
    dbData
      .orEmpty()
      .split(SEPARATOR)
      .filter { it.isNotBlank() }

  companion object {
    private const val SEPARATOR = ","
  }
}
