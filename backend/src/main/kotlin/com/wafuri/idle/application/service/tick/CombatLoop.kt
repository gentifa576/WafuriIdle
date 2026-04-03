package com.wafuri.idle.application.service.tick

import com.wafuri.idle.application.config.GameConfig
import com.wafuri.idle.application.port.out.CombatStateRepository
import com.wafuri.idle.application.service.combat.CombatTickService
import io.quarkus.runtime.Startup
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import jakarta.enterprise.context.ApplicationScoped
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

@Startup
@ApplicationScoped
class CombatLoop(
  private val combatStateRepository: CombatStateRepository,
  private val combatTickService: CombatTickService,
  private val gameConfig: GameConfig,
) {
  private val logger = LoggerFactory.getLogger(CombatLoop::class.java)
  private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  internal val zoneJobs = ConcurrentHashMap<String, Job>()
  private var coordinatorJob: Job? = null

  @PostConstruct
  fun start() {
    val tickInterval: Duration = gameConfig.tick().interval()
    val intervalMillis = tickInterval.toMillis().coerceAtLeast(1)
    coordinatorJob =
      scope.launchCorrectedLoop(intervalMillis) { _ ->
        try {
          syncZoneJobs(intervalMillis)
        } catch (exception: Exception) {
          logger
            .atError()
            .setCause(exception)
            .addKeyValue("tickIntervalMs", intervalMillis)
            .log("Combat loop coordination failed.")
        }
      }
  }

  internal fun syncZoneJobs(
    intervalMillis: Long =
      gameConfig
        .tick()
        .interval()
        .toMillis()
        .coerceAtLeast(1),
  ) {
    val activeZoneIds = combatStateRepository.findActiveZoneIds()
    activeZoneIds.forEach { zoneId ->
      zoneJobs.computeIfAbsent(zoneId) {
        createZoneJob(zoneId, intervalMillis)
      }
    }
    zoneJobs.keys
      .filter { it !in activeZoneIds }
      .forEach { zoneJobs.remove(it)?.cancel() }
  }

  @PreDestroy
  fun stop() {
    coordinatorJob?.cancel()
    zoneJobs.values.forEach { it.cancel() }
    zoneJobs.clear()
    scope.cancel()
  }

  private fun createZoneJob(
    zoneId: String,
    intervalMillis: Long,
  ): Job {
    val job =
      scope.launchCorrectedLoop(intervalMillis) { elapsed ->
        if (zoneId !in combatStateRepository.findActiveZoneIds()) {
          currentCoroutineContext().cancel()
          return@launchCorrectedLoop
        }
        try {
          combatTickService.tickZone(zoneId, elapsed)
        } catch (exception: Exception) {
          logger
            .atError()
            .setCause(exception)
            .addKeyValue("zoneId", zoneId)
            .addKeyValue("tickIntervalMs", intervalMillis)
            .addKeyValue("elapsedMillis", elapsed.toMillis())
            .log("Zone combat tick execution failed.")
        }
      }
    job.invokeOnCompletion {
      zoneJobs.remove(zoneId, job)
    }
    return job
  }
}
