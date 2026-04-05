package com.wafuri.idle.application.service.enemy

import com.wafuri.idle.application.exception.ResourceNotFoundException
import com.wafuri.idle.application.service.combat.RandomSource
import com.wafuri.idle.domain.model.EnemyTemplate
import jakarta.enterprise.context.ApplicationScoped
import java.util.concurrent.atomic.AtomicReference

@ApplicationScoped
class EnemyTemplateCatalog {
  private val enemies = AtomicReference<Map<String, EnemyTemplate>>(emptyMap())

  fun replace(newEnemies: Set<EnemyTemplate>) {
    enemies.set(newEnemies.associateBy { it.id })
  }

  fun all(): List<EnemyTemplate> = enemies.get().values.sortedBy { it.name }

  fun find(enemyId: String): EnemyTemplate? = enemies.get()[enemyId]

  fun require(enemyId: String): EnemyTemplate = find(enemyId) ?: throw ResourceNotFoundException("Enemy template $enemyId was not found.")

  fun requireRandom(
    enemyIds: List<String>,
    randomSource: RandomSource,
  ): EnemyTemplate {
    require(enemyIds.isNotEmpty()) { "Enemy id list must not be empty." }
    return require(enemyIds[randomSource.nextInt(enemyIds.size)])
  }
}
