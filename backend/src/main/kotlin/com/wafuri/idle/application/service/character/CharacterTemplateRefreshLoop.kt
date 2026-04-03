package com.wafuri.idle.application.service.character

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
class CharacterTemplateRefreshLoop(
  private val databaseCharacterFetcher: DatabaseCharacterFetcher,
  private val characterTemplateCatalog: CharacterTemplateCatalog,
  private val gameConfig: GameConfig,
) {
  private val logger = LoggerFactory.getLogger(CharacterTemplateRefreshLoop::class.java)
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
      val templates = databaseCharacterFetcher.fetch()
      if (templates.isNotEmpty()) {
        characterTemplateCatalog.replace(templates.toSet())
      }
    } catch (exception: Exception) {
      logger
        .atError()
        .setCause(exception)
        .addKeyValue("refreshIntervalMs", refreshIntervalMillis)
        .log("Character template refresh failed.")
    }
  }
}
