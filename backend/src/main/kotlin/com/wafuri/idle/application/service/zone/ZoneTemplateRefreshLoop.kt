package com.wafuri.idle.application.service.zone

import com.wafuri.idle.application.config.GameConfig
import com.wafuri.idle.application.service.TemplateRefreshLoop
import io.quarkus.arc.profile.IfBuildProfile
import io.quarkus.runtime.Startup
import jakarta.enterprise.context.ApplicationScoped

@Startup
@IfBuildProfile("prod")
@ApplicationScoped
class ZoneTemplateRefreshLoop(
  private val databaseZoneFetcher: DatabaseZoneFetcher,
  private val zoneTemplateCatalog: ZoneTemplateCatalog,
  private val gameConfig: GameConfig,
) : TemplateRefreshLoop(gameConfig) {
  override fun refresh() {
    val zones = databaseZoneFetcher.fetch()
    if (zones.isNotEmpty()) {
      zoneTemplateCatalog.replace(zones.toSet())
    }
  }

  override val failureMessage: String = "Zone template refresh failed."
}
