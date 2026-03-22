package com.wafuri.idle.application.service.character

import com.wafuri.idle.application.port.out.CharacterFetcher
import io.quarkus.runtime.Startup
import jakarta.annotation.PostConstruct
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Instance
import org.slf4j.LoggerFactory

@Startup
@ApplicationScoped
class CharacterTemplateBootstrap(
  private val databaseCharacterFetcher: DatabaseCharacterFetcher,
  private val resourceCharacterFetcherInstance: Instance<ResourceCharacterFetcher>,
  private val characterTemplateCatalog: CharacterTemplateCatalog,
) {
  private val logger = LoggerFactory.getLogger(CharacterTemplateBootstrap::class.java)

  @PostConstruct
  fun load() {
    val fetchers: List<CharacterFetcher> =
      buildList {
        if (resourceCharacterFetcherInstance.isResolvable) {
          add(resourceCharacterFetcherInstance.get())
        }
        add(databaseCharacterFetcher)
      }
    val templates =
      fetchers
        .asSequence()
        .map { it.fetch() }
        .firstOrNull { it.isNotEmpty() }
        .orEmpty()

    characterTemplateCatalog.replace(templates)

    if (templates.isEmpty()) {
      logger.warn("No character templates were loaded during startup.")
    } else {
      logger
        .atInfo()
        .addKeyValue("templateCount", templates.size)
        .log("Loaded character templates.")
    }
  }
}
