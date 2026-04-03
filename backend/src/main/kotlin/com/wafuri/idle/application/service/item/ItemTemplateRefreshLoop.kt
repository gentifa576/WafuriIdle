package com.wafuri.idle.application.service.item

import com.wafuri.idle.application.config.GameConfig
import io.quarkus.arc.profile.IfBuildProfile
import io.quarkus.runtime.Startup
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import jakarta.enterprise.context.ApplicationScoped
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

@Startup
@IfBuildProfile("prod")
@ApplicationScoped
class ItemTemplateRefreshLoop(
  private val databaseItemFetcher: DatabaseItemFetcher,
  private val itemTemplateCatalog: ItemTemplateCatalog,
  private val gameConfig: GameConfig,
) {
  private val logger = LoggerFactory.getLogger(ItemTemplateRefreshLoop::class.java)
  private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private var refreshJob: Job? = null

  @PostConstruct
  fun start() {
    val refreshInterval = gameConfig.content().refreshInterval()
    refreshJob =
      scope.launch {
        while (isActive) {
          delay(refreshInterval.toMillis())
          tryRefresh(refreshInterval.toMillis())
        }
      }
  }

  @PreDestroy
  fun stop() {
    refreshJob?.cancel()
    scope.cancel()
  }

  private fun tryRefresh(refreshIntervalMillis: Long) {
    try {
      val items = databaseItemFetcher.fetch()
      if (items.isNotEmpty()) {
        itemTemplateCatalog.replace(items.toSet())
      }
    } catch (exception: Exception) {
      logger
        .atError()
        .setCause(exception)
        .addKeyValue("refreshIntervalMs", refreshIntervalMillis)
        .log("Item template refresh failed.")
    }
  }
}
