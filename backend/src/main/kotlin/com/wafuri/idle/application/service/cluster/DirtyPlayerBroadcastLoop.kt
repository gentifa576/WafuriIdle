package com.wafuri.idle.application.service.cluster

import com.wafuri.idle.application.config.GameConfig
import com.wafuri.idle.application.service.tick.launchCorrectedLoop
import io.quarkus.runtime.Startup
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import jakarta.enterprise.context.ApplicationScoped
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.slf4j.LoggerFactory

@Startup
@ApplicationScoped
class DirtyPlayerBroadcastLoop(
  private val dirtyPlayerBroadcastService: DirtyPlayerBroadcastService,
  private val gameConfig: GameConfig,
) {
  private val logger = LoggerFactory.getLogger(DirtyPlayerBroadcastLoop::class.java)
  private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private var retryJob: Job? = null

  @PostConstruct
  fun start() {
    val intervalMillis =
      gameConfig
        .tick()
        .interval()
        .toMillis()
        .coerceAtLeast(1)
    retryJob =
      scope.launchCorrectedLoop(intervalMillis) { _ ->
        try {
          dirtyPlayerBroadcastService.flushPending()
        } catch (exception: Exception) {
          logger
            .atError()
            .setCause(exception)
            .log("Dirty player broadcast retry failed.")
        }
      }
  }

  @PreDestroy
  fun stop() {
    retryJob?.cancel()
    scope.cancel()
  }
}
