package com.wafuri.idle.application.port.out

import com.wafuri.idle.domain.model.AuthAccount
import java.util.UUID

interface AuthAccountRepository : Repository<AuthAccount, UUID> {
  fun findByUsername(username: String): AuthAccount?

  fun findByEmail(email: String): AuthAccount?
}
