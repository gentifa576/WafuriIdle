package com.wafuri.idle.application.port.out

import com.wafuri.idle.domain.model.CombatState
import java.util.UUID

interface CombatStateRepository : Repository<CombatState, UUID> {
  fun findActiveZoneIds(): Set<String>

  fun findActiveByZoneId(zoneId: String): List<CombatState>
}
