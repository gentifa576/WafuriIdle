package com.wafuri.idle.transport.rest

import com.wafuri.idle.application.service.auth.AuthService
import com.wafuri.idle.transport.rest.dto.LoginRequest
import com.wafuri.idle.transport.rest.dto.SignUpRequest
import com.wafuri.idle.transport.rest.dto.toResponse
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response

@Path("/auth")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
class AuthController(
  private val authService: AuthService,
) {
  @POST
  @Path("/signup")
  fun signup(request: SignUpRequest): Response =
    Response
      .status(Response.Status.CREATED)
      .entity(authService.signup(request.name, request.email, request.password).toResponse())
      .build()

  @POST
  @Path("/login")
  fun login(request: LoginRequest): Response =
    Response
      .ok(authService.login(request.name, request.email, request.password).toResponse())
      .build()
}
