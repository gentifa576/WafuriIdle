package com.wafuri.idle.application.service.enemy

import com.wafuri.idle.application.config.GameConfig
import com.wafuri.idle.application.service.TemplateRefreshLoop
import io.quarkus.arc.profile.IfBuildProfile
import io.quarkus.runtime.Startup
import jakarta.enterprise.context.ApplicationScoped

@Startup
@IfBuildProfile("prod")
@ApplicationScoped
class EnemyTemplateRefreshLoop(
  private val databaseEnemyFetcher: DatabaseEnemyFetcher,
  private val enemyTemplateCatalog: EnemyTemplateCatalog,
  private val gameConfig: GameConfig,
) : TemplateRefreshLoop(gameConfig) {
  override fun refresh() {
    val enemies = databaseEnemyFetcher.fetch()
    if (enemies.isNotEmpty()) {
      enemyTemplateCatalog.replace(enemies.toSet())
    }
  }

  override val failureMessage: String = "Enemy template refresh failed."
}
