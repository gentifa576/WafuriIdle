package com.wafuri.idle.transport.websocket

import com.fasterxml.jackson.databind.ObjectMapper
import com.wafuri.idle.application.config.GameConfig
import com.wafuri.idle.application.model.PlayerMessage
import com.wafuri.idle.application.port.out.PlayerMessagePublisher
import jakarta.enterprise.context.ApplicationScoped
import kotlinx.coroutines.delay
import java.util.UUID
import java.util.concurrent.ThreadLocalRandom

@ApplicationScoped
class WebSocketPlayerMessagePublisher(
  private val registry: PlayerWebSocketRegistry,
  private val objectMapper: ObjectMapper,
  private val gameConfig: GameConfig,
) : PlayerMessagePublisher {
  override suspend fun publish(message: PlayerMessage) {
    val jitterMillis = nextJitterMillis()
    if (jitterMillis > 0) {
      delay(jitterMillis)
    }
    val payload = objectMapper.writeValueAsString(message)
    send(message.playerId, payload)
  }

  private suspend fun nextJitterMillis(): Long {
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

  private suspend fun send(
    playerId: UUID,
    payload: String,
  ) = registry.sessions(playerId).forEach { it.sendText(payload).subscribe().with({}, {}) }
}
