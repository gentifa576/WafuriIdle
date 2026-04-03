package com.wafuri.idle.transport.rest

import com.wafuri.idle.application.exception.AuthorizationException
import com.wafuri.idle.application.port.out.TeamRepository
import com.wafuri.idle.application.service.auth.AuthSessionService
import io.quarkus.security.identity.SecurityIdentity
import jakarta.annotation.Priority
import jakarta.ws.rs.Priorities
import jakarta.ws.rs.container.ContainerRequestContext
import jakarta.ws.rs.container.ContainerRequestFilter
import jakarta.ws.rs.ext.Provider
import org.eclipse.microprofile.jwt.JsonWebToken
import java.util.UUID

@Provider
@TeamScopedAccess
@Priority(Priorities.AUTHORIZATION)
class TeamScopedAccessFilter(
  private val authContext: AuthContext,
  private val securityIdentity: SecurityIdentity,
  private val authSessionService: AuthSessionService,
  private val jwt: JsonWebToken,
  private val teamRepository: TeamRepository,
) : ContainerRequestFilter {
  override fun filter(requestContext: ContainerRequestContext) {
    authSessionService.requireActive(jwt)
    val requestedTeamId =
      requestContext.uriInfo.pathParameters["id"]
        ?.firstOrNull()
        ?.let(UUID::fromString)
        ?: throw AuthorizationException("Team access requires a team id path parameter.")
    val requestedTeam = teamRepository.findById(requestedTeamId) ?: throw AuthorizationException("Team access is forbidden.")
    val actorPlayerId = authContext.requirePlayerId()
    if (requestedTeam.playerId == actorPlayerId || securityIdentity.roles.contains("Dev") || securityIdentity.roles.contains("Admin")) {
      return
    }
    throw AuthorizationException("Team access is forbidden.")
  }
}
