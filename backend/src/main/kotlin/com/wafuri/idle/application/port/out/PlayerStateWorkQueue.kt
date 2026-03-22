package com.wafuri.idle.application.port.out

import java.util.UUID

interface PlayerStateWorkQueue {
  fun markDirty(playerId: UUID)

  fun drainDirtyPlayerIds(): Set<UUID>
}
