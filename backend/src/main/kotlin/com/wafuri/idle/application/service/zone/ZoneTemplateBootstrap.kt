package com.wafuri.idle.application.service.zone

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
class ZoneTemplateBootstrap(
  private val databaseZoneFetcher: DatabaseZoneFetcher,
  private val resourceZoneFetcherInstance: Instance<ResourceZoneFetcher>,
  private val zoneTemplateCatalog: ZoneTemplateCatalog,
) {
  private val logger = LoggerFactory.getLogger(ZoneTemplateBootstrap::class.java)

  @PostConstruct
  fun load() {
    val zones =
      loadFirstNonEmpty(
        optionalFetcher(resourceZoneFetcherInstance) { it.fetch() } +
          listOf({ databaseZoneFetcher.fetch() }),
      )

    zoneTemplateCatalog.replace(zones.toSet())
    logLoadedContent(
      logger = logger,
      content = zones,
      emptyMessage = "No zone templates were loaded during startup.",
      countKey = "zoneCount",
      loadedMessage = "Loaded zone templates.",
    )
  }
}
