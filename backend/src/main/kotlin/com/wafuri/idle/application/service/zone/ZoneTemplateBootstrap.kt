package com.wafuri.idle.application.service.zone

import com.wafuri.idle.application.port.out.ZoneFetcher
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
    val fetchers: List<ZoneFetcher> =
      buildList {
        if (resourceZoneFetcherInstance.isResolvable) {
          add(resourceZoneFetcherInstance.get())
        }
        add(databaseZoneFetcher)
      }
    val zones =
      fetchers
        .asSequence()
        .map { it.fetch() }
        .firstOrNull { it.isNotEmpty() }
        .orEmpty()

    zoneTemplateCatalog.replace(zones.toSet())

    if (zones.isEmpty()) {
      logger.warn("No zone templates were loaded during startup.")
    } else {
      logger
        .atInfo()
        .addKeyValue("zoneCount", zones.size)
        .log("Loaded zone templates.")
    }
  }
}
