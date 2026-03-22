package com.wafuri.idle.application.service.team

import com.wafuri.idle.application.exception.ResourceNotFoundException
import com.wafuri.idle.application.exception.ValidationException
import com.wafuri.idle.application.port.out.PlayerStateWorkQueue
import com.wafuri.idle.application.port.out.Repository
import com.wafuri.idle.application.port.out.TeamRepository
import com.wafuri.idle.application.service.character.CharacterTemplateCatalog
import com.wafuri.idle.domain.model.CharacterTemplate
import com.wafuri.idle.domain.model.DomainRuleViolationException
import com.wafuri.idle.domain.model.Player
import com.wafuri.idle.domain.model.Team
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import java.util.UUID

@ApplicationScoped
class TeamService(
  private val playerRepository: Repository<Player, UUID>,
  private val teamRepository: TeamRepository,
  private val characterTemplateCatalog: CharacterTemplateCatalog,
  private val playerStateWorkQueue: PlayerStateWorkQueue,
) {
  @Transactional
  fun create(playerId: UUID): Team {
    playerRepository.findById(playerId)
      ?: throw ResourceNotFoundException("Player $playerId was not found.")
    val team = Team(id = UUID.randomUUID(), playerId = playerId)
    val saved = teamRepository.save(team)
    playerStateWorkQueue.markDirty(playerId)
    return saved
  }

  fun listByPlayer(playerId: UUID): List<Team> = teamRepository.findByPlayerId(playerId)

  @Transactional
  fun assignCharacter(
    teamId: UUID,
    characterKey: String,
  ): Team {
    val team =
      teamRepository.findById(teamId)
        ?: throw ResourceNotFoundException("Team $teamId was not found.")
    val player =
      playerRepository.findById(team.playerId)
        ?: throw ResourceNotFoundException("Player ${team.playerId} was not found.")
    if (!player.ownedCharacterKeys.contains(characterKey)) {
      throw ValidationException("Character $characterKey is not owned by the player.")
    }
    characterTemplateCatalog.require(characterKey)

    val updatedTeam =
      try {
        team.addCharacter(characterKey)
      } catch (exception: DomainRuleViolationException) {
        throw ValidationException(exception.message ?: "Character validation failed.", exception)
      }

    val savedTeam = teamRepository.save(updatedTeam)
    playerStateWorkQueue.markDirty(team.playerId)
    return savedTeam
  }

  fun templates(): List<CharacterTemplate> = characterTemplateCatalog.all()

  @Transactional
  fun activate(teamId: UUID): Team {
    val team =
      teamRepository.findById(teamId)
        ?: throw ResourceNotFoundException("Team $teamId was not found.")
    val player =
      playerRepository.findById(team.playerId)
        ?: throw ResourceNotFoundException("Player ${team.playerId} was not found.")
    if (team.characterKeys.isEmpty()) {
      throw ValidationException("Team must have at least one character before it can be activated.")
    }
    if (team.characterKeys.any { !player.ownedCharacterKeys.contains(it) }) {
      throw ValidationException("Team contains a character the player does not own.")
    }

    playerRepository.save(player.activateTeam(team.id))
    playerStateWorkQueue.markDirty(team.playerId)
    return team
  }
}
