package com.wafuri.idle.application.port.out

import com.wafuri.idle.application.model.CombatSnapshot
import com.wafuri.idle.application.model.PlayerStateSnapshot
import java.util.UUID

interface PlayerStateChangeTracker {
  fun shouldPublishPlayerState(snapshot: PlayerStateSnapshot): Boolean

  fun shouldPublishCombatState(
    playerId: UUID,
    snapshot: CombatSnapshot?,
  ): Boolean

  fun invalidate(playerId: UUID)
}
