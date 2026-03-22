package com.wafuri.idle.transport.rest

import com.wafuri.idle.application.service.inventory.InventoryService
import com.wafuri.idle.application.service.player.PlayerService
import com.wafuri.idle.application.service.player.ProgressionService
import com.wafuri.idle.application.service.team.TeamService
import com.wafuri.idle.domain.model.InventoryItem
import com.wafuri.idle.domain.model.Player
import com.wafuri.idle.domain.model.PlayerZoneProgress
import com.wafuri.idle.domain.model.Team
import jakarta.annotation.security.RolesAllowed
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.MediaType
import java.util.UUID
import com.wafuri.idle.transport.rest.dto.ClaimStarterRequest

@Path("/players")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("User")
@PlayerScopedAccess
class PlayerController(
  private val playerService: PlayerService,
  private val progressionService: ProgressionService,
  private val teamService: TeamService,
  private val inventoryService: InventoryService,
) {
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

  @GET
  @Path("/{id}/zone-progress")
  fun zoneProgress(
    @PathParam("id") playerId: UUID,
  ): List<PlayerZoneProgress> = progressionService.listZoneProgress(playerId)

  @POST
  @Path("/{id}/starter")
  fun claimStarter(
    @PathParam("id") playerId: UUID,
    request: ClaimStarterRequest,
  ): Response {
    playerService.claimStarter(playerId, request.characterKey)
    return Response.noContent().build()
  }
}
