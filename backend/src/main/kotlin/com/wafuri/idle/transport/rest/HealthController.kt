package com.wafuri.idle.transport.rest

import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response

@Path("/health")
@Produces(MediaType.APPLICATION_JSON)
class HealthController {
  @GET
  fun health(): Response = Response.noContent().build()
}
