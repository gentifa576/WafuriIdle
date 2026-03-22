package com.wafuri.idle.transport.rest

import com.wafuri.idle.application.service.team.TeamService
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import java.util.UUID

@Path("/teams")
@Produces(MediaType.APPLICATION_JSON)
class TeamController(
  private val teamService: TeamService,
) {
  @POST
  @Path("/{id}/characters/{characterKey}")
  fun assignCharacter(
    @PathParam("id") teamId: UUID,
    @PathParam("characterKey") characterKey: String,
  ): Response = Response.ok(teamService.assignCharacter(teamId, characterKey)).build()

  @POST
  @Path("/{id}/activate")
  fun activate(
    @PathParam("id") teamId: UUID,
  ): Response = Response.ok(teamService.activate(teamId)).build()
}
