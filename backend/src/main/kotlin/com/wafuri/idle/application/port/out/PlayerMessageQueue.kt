package com.wafuri.idle.application.port.out

import com.wafuri.idle.application.model.PlayerMessage
import java.util.UUID

interface PlayerMessageQueue {
  fun enqueue(message: PlayerMessage)

  fun drainGroupedByPlayerId(): Map<UUID, List<PlayerMessage>>
}
