package com.wafuri.idle.application.service.cluster

import com.wafuri.idle.application.config.ClusterConfig
import com.wafuri.idle.application.port.out.ClusterNodeRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.context.control.ActivateRequestContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@ApplicationScoped
class DirtyPlayerBroadcastService(
  private val clusterConfig: ClusterConfig,
  private val clusterNodeRepository: ClusterNodeRepository,
  private val clusterNodeHeartbeatService: ClusterNodeHeartbeatService,
  private val internalDirtyNotificationClient: InternalDirtyNotificationClient,
) {
  private val logger = LoggerFactory.getLogger(DirtyPlayerBroadcastService::class.java)
  private val pendingAttempts = ConcurrentHashMap<UUID, Int>()

  fun enqueue(playerId: UUID) {
    pendingAttempts.putIfAbsent(playerId, 0)
  }

  @ActivateRequestContext
  suspend fun flushPending() {
    val pendingPlayerIds = pendingAttempts.keys.toList()
    if (pendingPlayerIds.isEmpty()) {
      return
    }

    val identity = clusterNodeHeartbeatService.currentIdentity()
    val cutoff = Instant.now().minus(clusterConfig.discovery().staleAfter())
    val peers =
      clusterNodeRepository
        .findAliveSince(cutoff)
        .filterNot { it.instanceId == identity.instanceId }
    if (peers.isEmpty()) {
      pendingPlayerIds.forEach(pendingAttempts::remove)
      return
    }

    coroutineScope {
      pendingPlayerIds
        .map { playerId ->
          async {
            try {
              peers.forEach { peer ->
                internalDirtyNotificationClient.notifyDirty(peer.internalBaseUrl, playerId)
              }
              pendingAttempts.remove(playerId)
            } catch (exception: Exception) {
              logger
                .atWarn()
                .setCause(exception)
                .addKeyValue("playerId", playerId)
                .log("Dirty player broadcast failed.")

              val attempts = (pendingAttempts[playerId] ?: 0) + 1
              if (attempts >= clusterConfig.notify().maxAttempts()) {
                pendingAttempts.remove(playerId)
                logger
                  .atWarn()
                  .addKeyValue("playerId", playerId)
                  .addKeyValue("attempts", attempts)
                  .log("Dropping dirty player broadcast after max retry attempts.")
              } else {
                pendingAttempts[playerId] = attempts
              }
            }
          }
        }.awaitAll()
    }
  }
}
