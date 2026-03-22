package com.wafuri.idle.application.port.out

import java.util.UUID

interface ActivePlayerRegistry {
  fun activePlayerIds(): Set<UUID>
}
