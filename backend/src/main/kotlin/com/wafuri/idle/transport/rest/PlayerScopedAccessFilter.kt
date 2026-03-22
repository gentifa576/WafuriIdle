package com.wafuri.idle.transport.rest

import com.wafuri.idle.application.exception.AuthorizationException
import io.quarkus.security.identity.SecurityIdentity
import jakarta.annotation.Priority
import jakarta.ws.rs.Priorities
import jakarta.ws.rs.container.ContainerRequestContext
import jakarta.ws.rs.container.ContainerRequestFilter
import jakarta.ws.rs.ext.Provider
import java.util.UUID

@Provider
@PlayerScopedAccess
@Priority(Priorities.AUTHORIZATION)
class PlayerScopedAccessFilter(
  private val authContext: AuthContext,
  private val securityIdentity: SecurityIdentity,
) : ContainerRequestFilter {
  override fun filter(requestContext: ContainerRequestContext) {
    val requestedPlayerId =
      requestContext.uriInfo.pathParameters["id"]
        ?.firstOrNull()
        ?.let(UUID::fromString)
        ?: throw AuthorizationException("Player access requires a player id path parameter.")
    val actorPlayerId = authContext.requirePlayerId()
    if (requestedPlayerId == actorPlayerId || securityIdentity.roles.contains("Dev") || securityIdentity.roles.contains("Admin")) {
      return
    }
    throw AuthorizationException("Player access is forbidden.")
  }
}
