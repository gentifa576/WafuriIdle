package com.wafuri.idle.transport.rest

import com.wafuri.idle.application.config.GameConfig
import com.wafuri.idle.application.service.character.CharacterTemplateCatalog
import com.wafuri.idle.application.service.team.TeamService
import com.wafuri.idle.domain.model.CharacterTemplate
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType

@Path("/characters")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
class CharacterController(
  private val gameConfig: GameConfig,
  private val characterTemplateCatalog: CharacterTemplateCatalog,
  private val teamService: TeamService,
) {
  @GET
  @Path("/templates")
  fun templates(): List<CharacterTemplate> = teamService.templates()

  @GET
  @Path("/starters")
  fun starters(): List<CharacterTemplate> = characterTemplateCatalog.requireAll(gameConfig.team().starterChoices())
}
