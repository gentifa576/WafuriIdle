package com.wafuri.idle.transport.websocket

import com.fasterxml.jackson.databind.ObjectMapper
import com.wafuri.idle.application.model.CombatStateMessage
import com.wafuri.idle.application.port.out.PlayerStateChangeTracker
import com.wafuri.idle.application.port.out.PlayerStateWorkQueue
import com.wafuri.idle.application.service.combat.CombatService
import com.wafuri.idle.application.service.player.OfflineProgressionService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.websocket.OnClose
import jakarta.websocket.OnError
import jakarta.websocket.OnMessage
import jakarta.websocket.OnOpen
import jakarta.websocket.Session
import jakarta.websocket.server.PathParam
import jakarta.websocket.server.ServerEndpoint
import org.eclipse.microprofile.context.ManagedExecutor
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID

@ApplicationScoped
@ServerEndpoint("/ws/player/{playerId}")
class PlayerWebSocketEndpoint {
  private val logger = LoggerFactory.getLogger(PlayerWebSocketEndpoint::class.java)

  @Inject
  lateinit var registry: PlayerWebSocketRegistry

  @Inject
  lateinit var playerStateWorkQueue: PlayerStateWorkQueue

  @Inject
  lateinit var playerStateChangeTracker: PlayerStateChangeTracker

  @Inject
  lateinit var offlineProgressionService: OfflineProgressionService

  @Inject
  lateinit var backgroundExecutor: ManagedExecutor

  @Inject
  lateinit var combatService: CombatService

  @Inject
  lateinit var objectMapper: ObjectMapper

  @OnOpen
  fun onOpen(
    session: Session,
    @PathParam("playerId") playerId: String,
  ) {
    val parsedPlayerId = UUID.fromString(playerId)
    registry.register(parsedPlayerId, session)
    playerStateChangeTracker.invalidate(parsedPlayerId)
    playerStateWorkQueue.markDirty(parsedPlayerId)
    backgroundExecutor.execute {
      runCatching {
        offlineProgressionService.applyIfNeeded(parsedPlayerId)
      }.onSuccess {
        playerStateChangeTracker.invalidate(parsedPlayerId)
        playerStateWorkQueue.markDirty(parsedPlayerId)
      }.onFailure { throwable ->
        logger
          .atWarn()
          .setCause(throwable)
          .addKeyValue("playerId", parsedPlayerId)
          .log("Offline progression apply failed during websocket open.")
      }
    }
  }

  @OnClose
  fun onClose(
    session: Session,
    @PathParam("playerId") playerId: String,
  ) {
    registry.unregister(UUID.fromString(playerId), session)
  }

  @OnMessage
  fun onMessage(
    message: String,
    session: Session,
  ) {
    val playerId =
      session.pathParameters["playerId"]
        ?: run {
          logger.atWarn().log("Ignoring websocket player command without a player id path parameter.")
          return
        }
    val command = objectMapper.readValue(message, PlayerSocketCommand::class.java)
    runCatching {
      when (command.type) {
        PlayerSocketCommandType.START_COMBAT -> {
          logger.atInfo().addKeyValue("playerId", playerId).log("Received websocket combat start command.")
          val parsedPlayerId = UUID.fromString(playerId)
          backgroundExecutor.execute {
            runCatching {
              val snapshot = combatService.start(parsedPlayerId)
              val payload =
                objectMapper.writeValueAsString(
                  CombatStateMessage(
                    playerId = parsedPlayerId,
                    snapshot = snapshot,
                    serverTime = Instant.now(),
                  ),
                )
              session.basicRemote.sendText(payload)
              playerStateChangeTracker.invalidate(parsedPlayerId)
              playerStateWorkQueue.markDirty(parsedPlayerId)
            }.onFailure { throwable ->
              logger
                .atWarn()
                .setCause(throwable)
                .addKeyValue("playerId", playerId)
                .addKeyValue("commandType", command.type)
                .log("Websocket player command failed.")
            }
          }
        }
      }
    }
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

data class PlayerSocketCommand(
  val type: PlayerSocketCommandType,
)

enum class PlayerSocketCommandType {
  START_COMBAT,
}
