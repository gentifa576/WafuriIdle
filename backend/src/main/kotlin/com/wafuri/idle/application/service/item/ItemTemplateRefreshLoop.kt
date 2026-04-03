package com.wafuri.idle.application.service.item

import com.wafuri.idle.application.config.GameConfig
import com.wafuri.idle.application.service.TemplateRefreshLoop
import io.quarkus.arc.profile.IfBuildProfile
import io.quarkus.runtime.Startup
import jakarta.enterprise.context.ApplicationScoped

@Startup
@IfBuildProfile("prod")
@ApplicationScoped
class ItemTemplateRefreshLoop(
  private val databaseItemFetcher: DatabaseItemFetcher,
  private val itemTemplateCatalog: ItemTemplateCatalog,
  private val gameConfig: GameConfig,
) : TemplateRefreshLoop(gameConfig) {
  override fun refresh() {
    val items = databaseItemFetcher.fetch()
    if (items.isNotEmpty()) {
      itemTemplateCatalog.replace(items.toSet())
    }
  }

  override val failureMessage: String = "Item template refresh failed."
}
