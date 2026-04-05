package com.wafuri.idle.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "enemy_templates")
class EnemyTemplateEntity {
  @Id
  @Column(name = "enemy_id", nullable = false, updatable = false)
  lateinit var id: String

  @Column(nullable = false)
  lateinit var name: String

  @Column
  var image: String? = null

  @Column(name = "base_hp", nullable = false)
  var baseHp: Float = 0f

  @Column(nullable = false)
  var attack: Float = 0f
}
