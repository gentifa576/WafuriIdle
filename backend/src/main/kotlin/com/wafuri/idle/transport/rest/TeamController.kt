package com.wafuri.idle.transport.rest

import com.wafuri.idle.application.service.team.TeamService
import com.wafuri.idle.domain.model.Team
import com.wafuri.idle.transport.rest.dto.SaveTeamLoadoutRequest
import jakarta.annotation.security.RolesAllowed
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import java.util.UUID

@Path("/teams")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("User")
@TeamScopedAccess
class TeamController(
  private val teamService: TeamService,
) {
  @POST
  @Path("/{id}/activate")
  fun activate(
    @PathParam("id") teamId: UUID,
  ): Team = teamService.activate(teamId)

  @POST
  @Path("/{id}/loadout")
  fun saveLoadout(
    @PathParam("id") teamId: UUID,
    request: SaveTeamLoadoutRequest,
  ): Team = teamService.saveLoadout(teamId, request.slots)
}
