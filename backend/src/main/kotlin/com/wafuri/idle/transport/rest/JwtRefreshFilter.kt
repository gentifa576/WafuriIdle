package com.wafuri.idle.transport.rest

import com.wafuri.idle.application.service.auth.JwtTokenService
import io.quarkus.security.identity.SecurityIdentity
import jakarta.annotation.Priority
import jakarta.ws.rs.Priorities
import jakarta.ws.rs.container.ContainerRequestContext
import jakarta.ws.rs.container.ContainerResponseContext
import jakarta.ws.rs.container.ContainerResponseFilter
import jakarta.ws.rs.ext.Provider
import org.eclipse.microprofile.jwt.JsonWebToken

@Provider
@Priority(Priorities.AUTHORIZATION + 1)
class JwtRefreshFilter(
  private val securityIdentity: SecurityIdentity,
  private val jwt: JsonWebToken,
  private val jwtTokenService: JwtTokenService,
) : ContainerResponseFilter {
  override fun filter(
    requestContext: ContainerRequestContext,
    responseContext: ContainerResponseContext,
  ) {
    if (
      securityIdentity.isAnonymous ||
      requestContext.uriInfo.path.startsWith("auth/") ||
      securityIdentity.roles.contains("InternalNode")
    ) {
      return
    }
    if (jwt.claimNames == null || jwt.subject == null) {
      return
    }
    val refreshed = jwtTokenService.refresh(jwt)
    responseContext.headers.add("X-Session-Token", refreshed.token)
    responseContext.headers.add("X-Session-Expires-At", refreshed.expiresAt.toString())
  }
}
