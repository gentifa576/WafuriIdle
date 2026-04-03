package com.wafuri.idle.application.service.character

import com.wafuri.idle.application.config.GameConfig
import com.wafuri.idle.application.service.TemplateRefreshLoop
import io.quarkus.arc.profile.IfBuildProfile
import io.quarkus.runtime.Startup
import jakarta.enterprise.context.ApplicationScoped

@Startup
@IfBuildProfile("prod")
@ApplicationScoped
class CharacterTemplateRefreshLoop(
  private val databaseCharacterFetcher: DatabaseCharacterFetcher,
  private val characterTemplateCatalog: CharacterTemplateCatalog,
  private val gameConfig: GameConfig,
) : TemplateRefreshLoop(gameConfig) {
  override fun refresh() {
    val templates = databaseCharacterFetcher.fetch()
    if (templates.isNotEmpty()) {
      characterTemplateCatalog.replace(templates.toSet())
    }
  }

  override val failureMessage: String = "Character template refresh failed."
}
