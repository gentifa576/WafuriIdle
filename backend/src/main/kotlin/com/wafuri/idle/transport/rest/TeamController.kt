package com.wafuri.idle.transport.rest

import com.wafuri.idle.application.service.inventory.EquipmentService
import com.wafuri.idle.application.service.team.TeamService
import com.wafuri.idle.domain.model.Team
import com.wafuri.idle.transport.rest.dto.EquipItemRequest
import com.wafuri.idle.transport.rest.dto.UnequipItemRequest
import jakarta.annotation.security.RolesAllowed
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import java.util.UUID

@Path("/teams")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("User")
@TeamScopedAccess
class TeamController(
  private val teamService: TeamService,
  private val equipmentService: EquipmentService,
) {
  @POST
  @Path("/{id}/slots/{position}/characters/{characterKey}")
  fun assignCharacter(
    @PathParam("id") teamId: UUID,
    @PathParam("position") position: Int,
    @PathParam("characterKey") characterKey: String,
  ): Team = teamService.assignCharacter(teamId, position, characterKey)

  @POST
  @Path("/{id}/activate")
  fun activate(
    @PathParam("id") teamId: UUID,
  ): Team = teamService.activate(teamId)

  @POST
  @Path("/{id}/slots/{position}/equip")
  fun equip(
    @PathParam("id") teamId: UUID,
    @PathParam("position") position: Int,
    request: EquipItemRequest,
  ): Response {
    equipmentService.equip(teamId, position, request.inventoryItemId, request.slot)
    return Response.noContent().build()
  }

  @POST
  @Path("/{id}/slots/{position}/unequip")
  fun unequip(
    @PathParam("id") teamId: UUID,
    @PathParam("position") position: Int,
    request: UnequipItemRequest,
  ): Response {
    equipmentService.unequip(teamId, position, request.slot)
    return Response.noContent().build()
  }
}
