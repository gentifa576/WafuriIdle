package com.wafuri.idle.application.service.enemy

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
class EnemyTemplateBootstrap(
  private val databaseEnemyFetcher: DatabaseEnemyFetcher,
  private val resourceEnemyFetcherInstance: Instance<ResourceEnemyFetcher>,
  private val enemyTemplateCatalog: EnemyTemplateCatalog,
) {
  private val logger = LoggerFactory.getLogger(EnemyTemplateBootstrap::class.java)

  @PostConstruct
  fun load() {
    val enemies =
      loadFirstNonEmpty(
        optionalFetcher(resourceEnemyFetcherInstance) { it.fetch() } +
          listOf({ databaseEnemyFetcher.fetch() }),
      )

    enemyTemplateCatalog.replace(enemies.toSet())
    logLoadedContent(
      logger = logger,
      content = enemies,
      emptyMessage = "No enemy templates were loaded during startup.",
      countKey = "enemyCount",
      loadedMessage = "Loaded enemy templates.",
    )
  }
}
