package com.wafuri.idle.transport.websocket

import com.fasterxml.jackson.databind.ObjectMapper
import com.wafuri.idle.application.config.GameConfig
import com.wafuri.idle.application.model.CombatSnapshot
import com.wafuri.idle.application.model.PlayerStateSnapshot
import com.wafuri.idle.application.port.out.PlayerStatePublisher
import jakarta.enterprise.context.ApplicationScoped
import kotlinx.coroutines.delay
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ThreadLocalRandom

@ApplicationScoped
class WebSocketPlayerStatePublisher(
  private val registry: PlayerWebSocketRegistry,
  private val objectMapper: ObjectMapper,
  private val gameConfig: GameConfig,
) : PlayerStatePublisher {
  override suspend fun publishPlayerState(snapshot: PlayerStateSnapshot) {
    val jitterMillis = nextJitterMillis()
    if (jitterMillis > 0) {
      delay(jitterMillis)
    }
    val payload =
      objectMapper.writeValueAsString(
        PlayerStateMessage(
          type = "PLAYER_STATE_SYNC",
          playerId = snapshot.playerId,
          snapshot = snapshot,
        ),
      )
    send(snapshot.playerId, payload)
  }

  override suspend fun publishCombatState(
    playerId: UUID,
    snapshot: CombatSnapshot?,
    serverTime: Instant,
  ) {
    val jitterMillis = nextJitterMillis()
    if (jitterMillis > 0) {
      delay(jitterMillis)
    }
    val payload =
      objectMapper.writeValueAsString(
        CombatStateMessage(
          type = "COMBAT_STATE_SYNC",
          playerId = playerId,
          snapshot = snapshot,
          serverTime = serverTime,
        ),
      )
    send(playerId, payload)
  }

  private fun nextJitterMillis(): Long {
    val maxMillis =
      gameConfig
        .tick()
        .publishJitterMax()
        .toMillis()
        .coerceAtLeast(0)
    if (maxMillis == 0L) {
      return 0L
    }
    return ThreadLocalRandom.current().nextLong(maxMillis + 1)
  }

  private fun send(
    playerId: UUID,
    payload: String,
  ) {
    registry.sessions(playerId).forEach { session ->
      if (session.isOpen) {
        session.asyncRemote.sendText(payload)
      }
    }
  }
}

data class PlayerStateMessage(
  val type: String,
  val playerId: UUID,
  val snapshot: PlayerStateSnapshot,
)

data class CombatStateMessage(
  val type: String,
  val playerId: UUID,
  val snapshot: CombatSnapshot?,
  val serverTime: Instant,
)
