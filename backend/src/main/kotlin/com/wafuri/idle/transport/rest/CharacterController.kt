package com.wafuri.idle.transport.rest

import com.wafuri.idle.application.service.inventory.EquipmentService
import com.wafuri.idle.application.service.team.TeamService
import com.wafuri.idle.domain.model.CharacterTemplate
import com.wafuri.idle.transport.rest.dto.EquipItemRequest
import com.wafuri.idle.transport.rest.dto.UnequipItemRequest
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response

@Path("/characters")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
class CharacterController(
  private val teamService: TeamService,
  private val equipmentService: EquipmentService,
) {
  @GET
  @Path("/templates")
  fun templates(): List<CharacterTemplate> = teamService.templates()

  @POST
  @Path("/{key}/equip")
  fun equip(
    @PathParam("key") characterKey: String,
    request: EquipItemRequest,
  ): Response {
    equipmentService.equip(characterKey, request.inventoryItemId, request.slot)
    return Response.noContent().build()
  }

  @POST
  @Path("/{key}/unequip")
  fun unequip(
    @PathParam("key") characterKey: String,
    request: UnequipItemRequest,
  ): Response {
    equipmentService.unequip(characterKey, request.slot)
    return Response.noContent().build()
  }
}
