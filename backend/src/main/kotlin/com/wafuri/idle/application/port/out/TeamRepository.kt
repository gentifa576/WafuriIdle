package com.wafuri.idle.application.port.out

import com.wafuri.idle.domain.model.Team
import java.util.UUID

interface TeamRepository : Repository<Team, UUID> {
  fun findByPlayerId(playerId: UUID): List<Team>
}
