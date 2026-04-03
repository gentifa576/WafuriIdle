package com.wafuri.idle.application.service

import com.wafuri.idle.application.config.GameConfig
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

abstract class TemplateRefreshLoop(
  private val gameConfig: GameConfig,
) {
  private val logger = LoggerFactory.getLogger(javaClass)
  private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private var refreshJob: Job? = null

  protected abstract val failureMessage: String

  @PostConstruct
  fun start() {
    val refreshIntervalMillis = gameConfig.content().refreshInterval().toMillis()
    refreshJob =
      scope.launch {
        while (isActive) {
          delay(refreshIntervalMillis)
          try {
            refresh()
          } catch (exception: Exception) {
            logger
              .atError()
              .setCause(exception)
              .addKeyValue("refreshIntervalMs", refreshIntervalMillis)
              .log(failureMessage)
          }
        }
      }
  }

  @PreDestroy
  fun stop() {
    refreshJob?.cancel()
  }

  protected abstract fun refresh()
}
