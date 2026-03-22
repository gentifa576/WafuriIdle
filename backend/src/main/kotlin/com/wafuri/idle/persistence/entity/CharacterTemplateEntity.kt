package com.wafuri.idle.persistence.entity

import com.wafuri.idle.persistence.converter.StringListConverter
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "character_templates")
class CharacterTemplateEntity {
  @Id
  @Column(name = "template_key", nullable = false, updatable = false)
  lateinit var key: String

  @Column(nullable = false)
  lateinit var name: String

  @Column(nullable = false)
  var strengthBase: Float = 0f

  @Column(nullable = false)
  var strengthIncrement: Float = 0f

  @Column(nullable = false)
  var agilityBase: Float = 0f

  @Column(nullable = false)
  var agilityIncrement: Float = 0f

  @Column(nullable = false)
  var intelligenceBase: Float = 0f

  @Column(nullable = false)
  var intelligenceIncrement: Float = 0f

  @Column(nullable = false)
  var wisdomBase: Float = 0f

  @Column(nullable = false)
  var wisdomIncrement: Float = 0f

  @Column(nullable = false)
  var vitalityBase: Float = 0f

  @Column(nullable = false)
  var vitalityIncrement: Float = 0f

  @Column
  var image: String? = null

  @Convert(converter = StringListConverter::class)
  @Column(nullable = false)
  var skillRefs: List<String> = emptyList()

  @Column
  var passiveRef: String? = null
}
