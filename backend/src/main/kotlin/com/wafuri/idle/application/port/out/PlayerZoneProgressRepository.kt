package com.wafuri.idle.application.port.out

import com.wafuri.idle.domain.model.PlayerZoneProgress
import java.util.UUID

interface PlayerZoneProgressRepository {
  fun save(domain: PlayerZoneProgress): PlayerZoneProgress

  fun findByPlayerIdAndZoneId(
    playerId: UUID,
    zoneId: String,
  ): PlayerZoneProgress?

  fun findByPlayerId(playerId: UUID): List<PlayerZoneProgress>
}
