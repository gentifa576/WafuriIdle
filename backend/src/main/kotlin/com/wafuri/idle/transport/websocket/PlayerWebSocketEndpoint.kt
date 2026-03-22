package com.wafuri.idle.transport.websocket

import com.wafuri.idle.application.port.out.PlayerStateWorkQueue
import jakarta.inject.Inject
import jakarta.websocket.OnClose
import jakarta.websocket.OnError
import jakarta.websocket.OnOpen
import jakarta.websocket.Session
import jakarta.websocket.server.PathParam
import jakarta.websocket.server.ServerEndpoint
import java.util.UUID

@ServerEndpoint("/ws/player/{playerId}")
class PlayerWebSocketEndpoint {
  @Inject
  lateinit var registry: PlayerWebSocketRegistry

  @Inject
  lateinit var playerStateWorkQueue: PlayerStateWorkQueue

  @OnOpen
  fun onOpen(
    session: Session,
    @PathParam("playerId") playerId: String,
  ) {
    val parsedPlayerId = UUID.fromString(playerId)
    registry.register(parsedPlayerId, session)
    playerStateWorkQueue.markDirty(parsedPlayerId)
  }

  @OnClose
  fun onClose(
    session: Session,
    @PathParam("playerId") playerId: String,
  ) {
    registry.unregister(UUID.fromString(playerId), session)
  }

  @OnError
  fun onError(
    session: Session,
    @PathParam("playerId") playerId: String,
    @Suppress("UNUSED_PARAMETER") throwable: Throwable,
  ) {
    registry.unregister(UUID.fromString(playerId), session)
  }
}
