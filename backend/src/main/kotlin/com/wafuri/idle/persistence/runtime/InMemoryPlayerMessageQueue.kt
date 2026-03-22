package com.wafuri.idle.persistence.runtime

import com.wafuri.idle.application.model.PlayerMessage
import com.wafuri.idle.application.port.out.PlayerMessageQueue
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@ApplicationScoped
class InMemoryPlayerMessageQueue : PlayerMessageQueue {
  private val eventsByPlayerId = ConcurrentHashMap<UUID, MutableList<PlayerMessage>>()

  override fun enqueue(message: PlayerMessage) {
    eventsByPlayerId.compute(message.playerId) { _, existing ->
      (existing ?: mutableListOf()).also { it += message }
    }
  }

  override fun drainGroupedByPlayerId(): Map<UUID, List<PlayerMessage>> {
    val drained = eventsByPlayerId.mapValues { (_, events) -> events.toList() }
    eventsByPlayerId.keys.removeAll(drained.keys)
    return drained
  }
}
