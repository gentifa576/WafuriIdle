package com.wafuri.idle.transport.websocket

import com.wafuri.idle.application.port.out.ActivePlayerRegistry
import jakarta.enterprise.context.ApplicationScoped
import jakarta.websocket.Session
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@ApplicationScoped
class PlayerWebSocketRegistry : ActivePlayerRegistry {
  private val sessionsByPlayer: MutableMap<UUID, MutableSet<Session>> = ConcurrentHashMap()

  fun register(
    playerId: UUID,
    session: Session,
  ) {
    sessionsByPlayer.computeIfAbsent(playerId) { ConcurrentHashMap.newKeySet() }.add(session)
  }

  fun unregister(
    playerId: UUID,
    session: Session,
  ) {
    sessionsByPlayer.computeIfPresent(playerId) { _, sessions ->
      sessions.remove(session)
      sessions.takeIf { it.isNotEmpty() }
    }
  }

  fun sessions(playerId: UUID): Set<Session> = sessionsByPlayer[playerId]?.toSet() ?: emptySet()

  override fun activePlayerIds(): Set<UUID> = sessionsByPlayer.keys.toSet()
}
