package com.wafuri.idle.transport.websocket

import com.wafuri.idle.application.port.out.ActivePlayerRegistry
import io.quarkus.websockets.next.WebSocketConnection
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@ApplicationScoped
class PlayerWebSocketRegistry : ActivePlayerRegistry {
  private val sessionsByPlayer: MutableMap<UUID, MutableSet<WebSocketConnection>> = ConcurrentHashMap()

  fun register(
    playerId: UUID,
    session: WebSocketConnection,
  ) {
    sessionsByPlayer.computeIfAbsent(playerId) { ConcurrentHashMap.newKeySet() }.add(session)
  }

  fun unregister(
    playerId: UUID,
    session: WebSocketConnection,
  ) {
    sessionsByPlayer.computeIfPresent(playerId) { _, sessions ->
      sessions.remove(session)
      sessions.takeIf { it.isNotEmpty() }
    }
  }

  fun sessions(playerId: UUID): Set<WebSocketConnection> = sessionsByPlayer[playerId]?.toSet() ?: emptySet()

  override fun activePlayerIds(): Set<UUID> = sessionsByPlayer.keys.toSet()
}
