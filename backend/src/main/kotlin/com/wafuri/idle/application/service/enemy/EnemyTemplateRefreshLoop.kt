package com.wafuri.idle.application.service.enemy

import com.wafuri.idle.application.config.GameConfig
import com.wafuri.idle.application.service.TemplateRefreshLoop
import com.wafuri.idle.application.service.refreshCatalogIfNotEmpty
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
    refreshCatalogIfNotEmpty(databaseEnemyFetcher::fetch, enemyTemplateCatalog::replace)
  }

  override val failureMessage: String = "Enemy template refresh failed."
}
