package com.wafuri.idle.application.service.tick

import com.wafuri.idle.application.config.GameConfig
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
import java.time.Duration

@Startup
@ApplicationScoped
class PlayerSyncLoop(
  private val gameTickService: GameTickService,
  private val gameConfig: GameConfig,
) {
  private val logger = LoggerFactory.getLogger(PlayerSyncLoop::class.java)
  private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private var tickJob: Job? = null

  @PostConstruct
  fun start() {
    val tickInterval: Duration = gameConfig.tick().interval()
    val intervalMillis = tickInterval.toMillis().coerceAtLeast(1)
    tickJob =
      scope.launchCorrectedLoop(intervalMillis) { _ ->
        try {
          gameTickService.tick()
        } catch (exception: Exception) {
          logger
            .atError()
            .setCause(exception)
            .addKeyValue("tickIntervalMs", intervalMillis)
            .log("Player sync tick execution failed.")
        }
      }
  }

  @PreDestroy
  fun stop() {
    tickJob?.cancel()
  }
}
