package com.wafuri.idle.application.service.cluster

import com.wafuri.idle.application.config.ClusterConfig
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
@ApplicationScoped
class ClusterNodeHeartbeatLoop(
  private val clusterConfig: ClusterConfig,
  private val clusterNodeHeartbeatService: ClusterNodeHeartbeatService,
) {
  private val logger = LoggerFactory.getLogger(ClusterNodeHeartbeatLoop::class.java)
  private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private var heartbeatJob: Job? = null

  @PostConstruct
  fun start() {
    tryHeartbeat()
    val intervalMillis =
      clusterConfig
        .discovery()
        .heartbeatInterval()
        .toMillis()
        .coerceAtLeast(1)
    heartbeatJob =
      scope.launch {
        while (isActive) {
          delay(intervalMillis)
          tryHeartbeat()
        }
      }
  }

  @PreDestroy
  fun stop() {
    heartbeatJob?.cancel()
    scope.cancel()
    runCatching { clusterNodeHeartbeatService.removeCurrentNode() }
      .onFailure { exception ->
        if (exception is IllegalStateException && exception.message?.contains("EntityManagerFactory is closed") == true) {
          return
        }
        logger
          .atWarn()
          .setCause(exception)
          .log("Cluster node deregistration failed during shutdown.")
      }
  }

  private fun tryHeartbeat() {
    runCatching { clusterNodeHeartbeatService.heartbeat() }
      .onFailure { exception ->
        logger
          .atError()
          .setCause(exception)
          .log("Cluster node heartbeat failed.")
      }
  }
}
