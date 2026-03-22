package com.wafuri.idle.persistence.converter

import com.wafuri.idle.domain.model.Stat
import com.wafuri.idle.domain.model.StatType
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

@Converter
class StatConverter : AttributeConverter<Stat, String> {
  override fun convertToDatabaseColumn(attribute: Stat?): String =
    attribute?.let { stat -> "${stat.type.name}$SEPARATOR${stat.value}" }.orEmpty()

  override fun convertToEntityAttribute(dbData: String?): Stat {
    val parts = dbData.orEmpty().split(SEPARATOR, limit = 2)
    require(parts.size == 2) { "Stat must be stored as STAT:value." }
    return Stat(type = StatType.valueOf(parts[0]), value = parts[1].toFloat())
  }

  companion object {
    private const val SEPARATOR = ":"
  }
}
