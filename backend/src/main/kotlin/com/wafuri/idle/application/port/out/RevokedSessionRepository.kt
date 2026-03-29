package com.wafuri.idle.application.port.out

import com.wafuri.idle.domain.model.RevokedSession
import java.util.UUID

interface RevokedSessionRepository : Repository<RevokedSession, UUID> {
  fun delete(sessionId: UUID)
}
