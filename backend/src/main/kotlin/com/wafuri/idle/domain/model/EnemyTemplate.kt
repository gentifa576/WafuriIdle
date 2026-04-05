package com.wafuri.idle.domain.model

data class EnemyTemplate(
  val id: String,
  val name: String,
  val image: String? = null,
  val baseHp: Float,
  val attack: Float,
) {
  init {
    require(id.isNotBlank()) { "Enemy id must not be blank." }
    require(name.isNotBlank()) { "Enemy name must not be blank." }
    require(image == null || image.isNotBlank()) { "Enemy image must not be blank when provided." }
    require(baseHp > 0f) { "Enemy base HP must be positive." }
    require(attack > 0f) { "Enemy attack must be positive." }
  }
}
