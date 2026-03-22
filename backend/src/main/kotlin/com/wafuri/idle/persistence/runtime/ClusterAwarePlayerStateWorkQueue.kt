package com.wafuri.idle.persistence.runtime

import com.wafuri.idle.application.port.out.PlayerStateWorkQueue
import com.wafuri.idle.application.service.cluster.DirtyPlayerBroadcastService
import io.quarkus.arc.DefaultBean
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

@DefaultBean
@ApplicationScoped
class ClusterAwarePlayerStateWorkQueue(
  private val localPlayerStateWorkQueue: LocalPlayerStateWorkQueue,
  private val dirtyPlayerBroadcastService: DirtyPlayerBroadcastService,
) : PlayerStateWorkQueue {
  override fun markDirty(playerId: UUID) {
    localPlayerStateWorkQueue.markDirtyLocal(playerId)
    dirtyPlayerBroadcastService.enqueue(playerId)
  }

  override fun drainDirtyPlayerIds(): Set<UUID> = localPlayerStateWorkQueue.drainDirtyPlayerIds()
}
