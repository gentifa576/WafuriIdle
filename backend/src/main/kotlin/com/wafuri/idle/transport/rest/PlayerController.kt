package com.wafuri.idle.transport.rest

import com.wafuri.idle.application.model.CombatSnapshot
import com.wafuri.idle.application.service.combat.CombatService
import com.wafuri.idle.application.service.inventory.InventoryService
import com.wafuri.idle.application.service.player.PlayerService
import com.wafuri.idle.application.service.team.TeamService
import com.wafuri.idle.domain.model.InventoryItem
import com.wafuri.idle.domain.model.Player
import com.wafuri.idle.domain.model.Team
import com.wafuri.idle.transport.rest.dto.CreatePlayerRequest
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import java.util.UUID

@Path("/players")
@Produces(MediaType.APPLICATION_JSON)
class PlayerController(
  private val playerService: PlayerService,
  private val teamService: TeamService,
  private val inventoryService: InventoryService,
  private val combatService: CombatService,
) {
  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  fun create(request: CreatePlayerRequest): Response {
    val player = playerService.create(request.name)
    return Response.status(Response.Status.CREATED).entity(player).build()
  }

  @GET
  @Path("/{id}")
  fun get(
    @PathParam("id") playerId: UUID,
  ): Player = playerService.get(playerId)

  @GET
  @Path("/{id}/teams")
  fun teams(
    @PathParam("id") playerId: UUID,
  ): List<Team> {
    playerService.get(playerId)
    return teamService.listByPlayer(playerId)
  }

  @GET
  @Path("/{id}/inventory")
  fun inventory(
    @PathParam("id") playerId: UUID,
  ): List<InventoryItem> = inventoryService.getInventory(playerId)

  @POST
  @Path("/{id}/combat/start")
  fun startCombat(
    @PathParam("id") playerId: UUID,
  ): CombatSnapshot = combatService.start(playerId)
}
