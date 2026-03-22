package com.wafuri.idle.application.service.combat

import com.wafuri.idle.application.exception.ResourceNotFoundException
import com.wafuri.idle.application.exception.ValidationException
import com.wafuri.idle.application.model.CharacterCombatStats
import com.wafuri.idle.application.model.TeamCombatStats
import com.wafuri.idle.application.port.out.Repository
import com.wafuri.idle.application.port.out.TeamRepository
import com.wafuri.idle.application.service.character.CharacterTemplateCatalog
import com.wafuri.idle.domain.model.Player
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

@ApplicationScoped
class CombatStatService(
  private val playerRepository: Repository<Player, UUID>,
  private val teamRepository: TeamRepository,
  private val characterTemplateCatalog: CharacterTemplateCatalog,
) {
  fun teamStatsForPlayer(playerId: UUID): TeamCombatStats {
    val player =
      playerRepository.findById(playerId)
        ?: throw ResourceNotFoundException("Player $playerId was not found.")
    val activeTeamId =
      player.activeTeamId
        ?: throw ValidationException("Player does not have an active team.")
    return teamStats(activeTeamId)
  }

  fun teamStats(teamId: UUID): TeamCombatStats {
    val team =
      teamRepository.findById(teamId)
        ?: throw ResourceNotFoundException("Team $teamId was not found.")
    if (team.characterKeys.isEmpty()) {
      throw ValidationException("Team must contain at least one character to enter combat.")
    }
    val player =
      playerRepository.findById(team.playerId)
        ?: throw ResourceNotFoundException("Player ${team.playerId} was not found.")

    return TeamCombatStats(
      teamId = team.id,
      characterStats =
        team.characterKeys.map { characterKey ->
          if (!player.ownedCharacterKeys.contains(characterKey)) {
            throw ValidationException("Team contains a character the player does not own.")
          }
          val template = characterTemplateCatalog.require(characterKey)
          CharacterCombatStats(
            characterKey = characterKey,
            attack = template.strength.base,
            hit = template.agility.base,
            maxHp = template.vitality.base,
          )
        },
    )
  }
}
