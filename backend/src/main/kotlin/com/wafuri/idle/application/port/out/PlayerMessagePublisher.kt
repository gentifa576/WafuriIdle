package com.wafuri.idle.application.port.out

import com.wafuri.idle.application.model.PlayerMessage

interface PlayerMessagePublisher {
  suspend fun publish(message: PlayerMessage)
}
