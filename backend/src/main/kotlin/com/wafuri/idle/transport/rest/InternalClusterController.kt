package com.wafuri.idle.transport.rest

import com.wafuri.idle.application.port.out.ActivePlayerRegistry
import com.wafuri.idle.persistence.runtime.LocalPlayerStateWorkQueue
import jakarta.annotation.security.RolesAllowed
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.core.Response
import java.util.UUID

@Path("/internal")
@RolesAllowed("InternalNode")
class InternalClusterController(
  private val activePlayerRegistry: ActivePlayerRegistry,
  private val localPlayerStateWorkQueue: LocalPlayerStateWorkQueue,
) {
  @POST
  @Path("/players/{id}/dirty")
  fun markPlayerDirty(
    @PathParam("id") playerId: UUID,
  ): Response {
    if (playerId in activePlayerRegistry.activePlayerIds()) {
      localPlayerStateWorkQueue.markDirtyLocal(playerId)
    }
    return Response.accepted().build()
  }
}
