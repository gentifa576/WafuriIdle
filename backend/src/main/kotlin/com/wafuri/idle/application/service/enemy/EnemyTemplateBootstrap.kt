package com.wafuri.idle.application.service.enemy

import com.wafuri.idle.application.port.out.EnemyFetcher
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
    val fetchers: List<EnemyFetcher> =
      buildList {
        if (resourceEnemyFetcherInstance.isResolvable) {
          add(resourceEnemyFetcherInstance.get())
        }
        add(databaseEnemyFetcher)
      }
    val enemies =
      fetchers
        .asSequence()
        .map { it.fetch() }
        .firstOrNull { it.isNotEmpty() }
        .orEmpty()

    enemyTemplateCatalog.replace(enemies.toSet())

    if (enemies.isEmpty()) {
      logger.warn("No enemy templates were loaded during startup.")
    } else {
      logger
        .atInfo()
        .addKeyValue("enemyCount", enemies.size)
        .log("Loaded enemy templates.")
    }
  }
}
