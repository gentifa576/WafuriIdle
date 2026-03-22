package com.wafuri.idle.persistence.runtime

import com.wafuri.idle.application.port.out.PlayerStateWorkQueue
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@ApplicationScoped
class InMemoryPlayerStateWorkQueue : PlayerStateWorkQueue {
  private val dirtyPlayerIds = ConcurrentHashMap.newKeySet<UUID>()

  override fun markDirty(playerId: UUID) {
    dirtyPlayerIds.add(playerId)
  }

  override fun drainDirtyPlayerIds(): Set<UUID> {
    val drained = dirtyPlayerIds.toSet()
    dirtyPlayerIds.removeAll(drained)
    return drained
  }
}
