package com.wafuri.idle.application.service.item

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
class ItemTemplateBootstrap(
  private val databaseItemFetcher: DatabaseItemFetcher,
  private val resourceItemFetcherInstance: Instance<ResourceItemFetcher>,
  private val itemTemplateCatalog: ItemTemplateCatalog,
) {
  private val logger = LoggerFactory.getLogger(ItemTemplateBootstrap::class.java)

  @PostConstruct
  fun load() {
    val items =
      loadFirstNonEmpty(
        optionalFetcher(resourceItemFetcherInstance) { it.fetch() } +
          listOf({ databaseItemFetcher.fetch() }),
      )

    itemTemplateCatalog.replace(items.toSet())
    logLoadedContent(
      logger = logger,
      content = items,
      emptyMessage = "No item templates were loaded during startup.",
      countKey = "itemCount",
      loadedMessage = "Loaded item templates.",
    )
  }
}
