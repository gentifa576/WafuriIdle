package com.wafuri.idle.persistence.runtime

import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@ApplicationScoped
class LocalPlayerStateWorkQueue {
  private val dirtyPlayerIds = ConcurrentHashMap.newKeySet<UUID>()

  fun markDirtyLocal(playerId: UUID) {
    dirtyPlayerIds.add(playerId)
  }

  fun drainDirtyPlayerIds(): Set<UUID> {
    val drained = dirtyPlayerIds.toSet()
    dirtyPlayerIds.removeAll(drained)
    return drained
  }
}
