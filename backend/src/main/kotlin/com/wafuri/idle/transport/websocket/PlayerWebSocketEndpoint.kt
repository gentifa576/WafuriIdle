package com.wafuri.idle.transport.websocket

import com.wafuri.idle.application.exception.AuthorizationException
import com.wafuri.idle.application.exception.ValidationException
import com.wafuri.idle.application.model.CombatStateMessage
import com.wafuri.idle.application.model.CommandErrorMessage
import com.wafuri.idle.application.port.out.PlayerStateChangeTracker
import com.wafuri.idle.application.port.out.PlayerStateWorkQueue
import com.wafuri.idle.application.service.combat.CombatService
import com.wafuri.idle.application.service.player.OfflineProgressionService
import io.quarkus.security.Authenticated
import io.quarkus.websockets.next.OnClose
import io.quarkus.websockets.next.OnOpen
import io.quarkus.websockets.next.OnTextMessage
import io.quarkus.websockets.next.WebSocket
import io.quarkus.websockets.next.WebSocketConnection
import io.smallrye.common.annotation.Blocking
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.eclipse.microprofile.context.ManagedExecutor
import org.eclipse.microprofile.jwt.JsonWebToken
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID

@Authenticated
@WebSocket(path = "/ws/player/{playerId}")
@ApplicationScoped
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
  lateinit var jwt: JsonWebToken

  @OnOpen
  fun onOpen(connection: WebSocketConnection) {
    val parsedPlayerId = requireAuthorizedPlayer(connection.pathParam("playerId"))
    registry.register(parsedPlayerId, connection)
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
  fun onClose(connection: WebSocketConnection) {
    registry.unregister(UUID.fromString(connection.pathParam("playerId")), connection)
  }

  @Blocking
  @OnTextMessage
  fun onMessage(
    command: PlayerSocketCommand,
    connection: WebSocketConnection,
  ): Any? {
    val playerId = connection.pathParam("playerId")
    val parsedPlayerId = requireAuthorizedPlayer(playerId)
    return try {
      when (command.type) {
        PlayerSocketCommandType.START_COMBAT -> {
          logger.atInfo().addKeyValue("playerId", playerId).log("Received websocket combat start command.")
          val snapshot = combatService.start(parsedPlayerId)
          playerStateChangeTracker.invalidate(parsedPlayerId)
          playerStateWorkQueue.markDirty(parsedPlayerId)
          CombatStateMessage(
            playerId = parsedPlayerId,
            snapshot = snapshot,
            serverTime = Instant.now(),
          )
        }
      }
    } catch (exception: ValidationException) {
      logger
        .atInfo()
        .setCause(exception)
        .addKeyValue("playerId", parsedPlayerId)
        .addKeyValue("commandType", command.type)
        .log("Rejected websocket command.")
      CommandErrorMessage(
        playerId = parsedPlayerId,
        commandType = command.type.name,
        message = exception.message ?: "Command rejected.",
        serverTime = Instant.now(),
      )
    }
  }

  internal fun requireAuthorizedPlayer(playerId: String): UUID {
    val requestedPlayerId = UUID.fromString(playerId)
    val authenticatedPlayerId = UUID.fromString(jwt.subject)
    if (requestedPlayerId != authenticatedPlayerId) {
      throw AuthorizationException("WebSocket player access is forbidden.")
    }
    return requestedPlayerId
  }
}

data class PlayerSocketCommand(
  val type: PlayerSocketCommandType,
)

enum class PlayerSocketCommandType {
  START_COMBAT,
}
