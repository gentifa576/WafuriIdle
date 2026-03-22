package com.wafuri.idle.transport.rest

import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import org.eclipse.microprofile.jwt.JsonWebToken
import java.util.UUID

@RequestScoped
class AuthContext {
  @Inject
  lateinit var jwt: JsonWebToken

  fun requirePlayerId(): UUID = UUID.fromString(jwt.subject)
}
