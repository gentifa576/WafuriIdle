package com.wafuri.idle.application.service.player

import com.wafuri.idle.application.config.GameConfig
import com.wafuri.idle.application.exception.ResourceNotFoundException
import com.wafuri.idle.application.exception.ValidationException
import com.wafuri.idle.application.port.out.Repository
import com.wafuri.idle.application.port.out.TeamRepository
import com.wafuri.idle.application.service.character.CharacterTemplateCatalog
import com.wafuri.idle.domain.model.Player
import com.wafuri.idle.domain.model.Team
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import java.util.UUID

@ApplicationScoped
class PlayerService(
  private val playerRepository: Repository<Player, UUID>,
  private val teamRepository: TeamRepository,
  private val characterTemplateCatalog: CharacterTemplateCatalog,
  private val gameConfig: GameConfig,
) {
  @Transactional
  fun provision(name: String): Player {
    val player =
      Player(
        id = UUID.randomUUID(),
        name = name,
        experience = 0,
        level = 1,
      )
    val savedPlayer = playerRepository.save(player)
    repeat(gameConfig.team().initialSlots().coerceAtLeast(0)) {
      teamRepository.save(Team(id = UUID.randomUUID(), playerId = savedPlayer.id))
    }
    return savedPlayer
  }

  @Transactional
  fun claimStarter(
    playerId: UUID,
    characterKey: String,
  ): Player {
    if (characterKey !in gameConfig.team().starterChoices()) {
      throw ValidationException("Starter character $characterKey is not allowed.")
    }
    val player = get(playerId)
    if (player.ownedCharacterKeys.isNotEmpty()) {
      throw ValidationException("Starter choice is only available for players without owned characters.")
    }
    characterTemplateCatalog.require(characterKey)
    return playerRepository.save(player.grantCharacter(characterKey))
  }

  fun get(playerId: UUID): Player =
    playerRepository.findById(playerId)
      ?: throw ResourceNotFoundException("Player $playerId was not found.")
}
