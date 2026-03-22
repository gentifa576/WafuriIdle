package com.wafuri.idle.application.port.out

import com.wafuri.idle.application.model.CombatSnapshot
import com.wafuri.idle.application.model.PlayerStateSnapshot
import java.time.Instant
import java.util.UUID

interface PlayerStatePublisher {
  suspend fun publishPlayerState(snapshot: PlayerStateSnapshot)

  suspend fun publishCombatState(
    playerId: UUID,
    snapshot: CombatSnapshot?,
    serverTime: Instant,
  )
}
