package com.wafuri.idle.application.service.character

import com.wafuri.idle.application.service.loadFirstNonEmpty
import com.wafuri.idle.application.service.logLoadedContent
import com.wafuri.idle.application.service.optionalFetcher
import io.quarkus.runtime.Startup
import jakarta.annotation.PostConstruct
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Instance
import org.slf4j.LoggerFactory

@Startup
@ApplicationScoped
class CharacterTemplateBootstrap(
  private val databaseCharacterFetcher: DatabaseCharacterFetcher,
  private val localCharacterFetcherInstance: Instance<LocalCharacterFetcher>,
  private val characterTemplateCatalog: CharacterTemplateCatalog,
) {
  private val logger = LoggerFactory.getLogger(CharacterTemplateBootstrap::class.java)

  @PostConstruct
  fun load() {
    val templates =
      loadFirstNonEmpty(
        optionalFetcher(localCharacterFetcherInstance) { it.fetch() } +
          listOf({ databaseCharacterFetcher.fetch() }),
      )

    characterTemplateCatalog.replace(templates.toSet())
    logLoadedContent(
      logger = logger,
      content = templates,
      emptyMessage = "No character templates were loaded during startup.",
      countKey = "templateCount",
      loadedMessage = "Loaded character templates.",
    )
  }
}
