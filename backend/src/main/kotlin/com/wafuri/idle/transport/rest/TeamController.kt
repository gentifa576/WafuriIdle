package com.wafuri.idle.transport.rest

import com.wafuri.idle.application.service.inventory.EquipmentService
import com.wafuri.idle.application.service.team.TeamService
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
class TeamController(
  private val authContext: AuthContext,
  private val teamService: TeamService,
  private val equipmentService: EquipmentService,
) {
  @POST
  @Path("/{id}/slots/{position}/characters/{characterKey}")
  fun assignCharacter(
    @PathParam("id") teamId: UUID,
    @PathParam("position") position: Int,
    @PathParam("characterKey") characterKey: String,
  ): Response =
    Response
      .ok(teamService.assignCharacter(authContext.requirePlayerId(), teamId, position, characterKey))
      .build()

  @POST
  @Path("/{id}/activate")
  fun activate(
    @PathParam("id") teamId: UUID,
  ): Response = Response.ok(teamService.activate(authContext.requirePlayerId(), teamId)).build()

  @POST
  @Path("/{id}/slots/{position}/equip")
  fun equip(
    @PathParam("id") teamId: UUID,
    @PathParam("position") position: Int,
    request: EquipItemRequest,
  ): Response {
    equipmentService.equip(authContext.requirePlayerId(), teamId, position, request.inventoryItemId, request.slot)
    return Response.noContent().build()
  }

  @POST
  @Path("/{id}/slots/{position}/unequip")
  fun unequip(
    @PathParam("id") teamId: UUID,
    @PathParam("position") position: Int,
    request: UnequipItemRequest,
  ): Response {
    equipmentService.unequip(authContext.requirePlayerId(), teamId, position, request.slot)
    return Response.noContent().build()
  }
}
