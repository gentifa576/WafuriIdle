package com.wafuri.idle.application.service.item

import com.wafuri.idle.application.port.out.ItemFetcher
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
    val fetchers: List<ItemFetcher> =
      buildList {
        if (resourceItemFetcherInstance.isResolvable) {
          add(resourceItemFetcherInstance.get())
        }
        add(databaseItemFetcher)
      }
    val items =
      fetchers
        .asSequence()
        .map { it.fetch() }
        .firstOrNull { it.isNotEmpty() }
        .orEmpty()

    itemTemplateCatalog.replace(items.toSet())

    if (items.isEmpty()) {
      logger.warn("No item templates were loaded during startup.")
    } else {
      logger
        .atInfo()
        .addKeyValue("itemCount", items.size)
        .log("Loaded item templates.")
    }
  }
}
