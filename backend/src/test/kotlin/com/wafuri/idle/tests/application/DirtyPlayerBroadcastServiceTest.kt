package com.wafuri.idle.tests.application

import com.wafuri.idle.application.config.ClusterConfig
import com.wafuri.idle.application.model.ClusterNode
import com.wafuri.idle.application.port.out.ClusterNodeRepository
import com.wafuri.idle.application.service.cluster.ClusterNodeHeartbeatService
import com.wafuri.idle.application.service.cluster.DirtyPlayerBroadcastService
import com.wafuri.idle.application.service.cluster.InstanceIdentity
import com.wafuri.idle.application.service.cluster.InternalDirtyNotificationClient
import io.kotest.core.spec.style.StringSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Duration
import java.time.Instant
import java.util.UUID

class DirtyPlayerBroadcastServiceTest :
  StringSpec({
    "flushPending broadcasts to other live nodes and clears on success" {
      val clusterConfig = mockClusterConfig(maxAttempts = 3, staleAfter = Duration.ofSeconds(30))
      val clusterNodeRepository = mockk<ClusterNodeRepository>()
      val clusterNodeHeartbeatService = mockk<ClusterNodeHeartbeatService>()
      val internalDirtyNotificationClient = mockk<InternalDirtyNotificationClient>()
      val service =
        DirtyPlayerBroadcastService(
          clusterConfig = clusterConfig,
          clusterNodeRepository = clusterNodeRepository,
          clusterNodeHeartbeatService = clusterNodeHeartbeatService,
          internalDirtyNotificationClient = internalDirtyNotificationClient,
        )
      val playerId = UUID.randomUUID()
      val now = Instant.now()
      val peer = ClusterNode("node-b", "http://10.0.0.2:8080", now)

      every { clusterNodeHeartbeatService.currentIdentity() } returns InstanceIdentity("node-a", "http://10.0.0.1:8080")
      every { clusterNodeRepository.findAliveSince(any()) } returns listOf(peer)
      every { internalDirtyNotificationClient.notifyDirty(peer.internalBaseUrl, playerId) } returns true

      service.enqueue(playerId)
      service.flushPending()

      verify(exactly = 1) { internalDirtyNotificationClient.notifyDirty(peer.internalBaseUrl, playerId) }
      service.flushPending()
      verify(exactly = 1) { internalDirtyNotificationClient.notifyDirty(peer.internalBaseUrl, playerId) }
    }

    "flushPending drops player after max retry attempts" {
      val clusterConfig = mockClusterConfig(maxAttempts = 2, staleAfter = Duration.ofSeconds(30))
      val clusterNodeRepository = mockk<ClusterNodeRepository>()
      val clusterNodeHeartbeatService = mockk<ClusterNodeHeartbeatService>()
      val internalDirtyNotificationClient = mockk<InternalDirtyNotificationClient>()
      val service =
        DirtyPlayerBroadcastService(
          clusterConfig = clusterConfig,
          clusterNodeRepository = clusterNodeRepository,
          clusterNodeHeartbeatService = clusterNodeHeartbeatService,
          internalDirtyNotificationClient = internalDirtyNotificationClient,
        )
      val playerId = UUID.randomUUID()
      val peer = ClusterNode("node-b", "http://10.0.0.2:8080", Instant.now())

      every { clusterNodeHeartbeatService.currentIdentity() } returns InstanceIdentity("node-a", "http://10.0.0.1:8080")
      every { clusterNodeRepository.findAliveSince(any()) } returns listOf(peer)
      every { internalDirtyNotificationClient.notifyDirty(peer.internalBaseUrl, playerId) } returns false

      service.enqueue(playerId)
      service.flushPending()
      service.flushPending()
      service.flushPending()

      verify(exactly = 2) { internalDirtyNotificationClient.notifyDirty(peer.internalBaseUrl, playerId) }
    }
  })

private fun mockClusterConfig(
  maxAttempts: Int,
  staleAfter: Duration,
): ClusterConfig =
  object : ClusterConfig {
    override fun local(): ClusterConfig.Local = throw UnsupportedOperationException("Unused in test.")

    override fun aws(): ClusterConfig.Aws = throw UnsupportedOperationException("Unused in test.")

    override fun discovery(): ClusterConfig.Discovery =
      object : ClusterConfig.Discovery {
        override fun heartbeatInterval(): Duration = Duration.ofSeconds(10)

        override fun staleAfter(): Duration = staleAfter
      }

    override fun notify(): ClusterConfig.Notify =
      object : ClusterConfig.Notify {
        override fun requestTimeout(): Duration = Duration.ofMillis(500)

        override fun maxAttempts(): Int = maxAttempts
      }
  }
