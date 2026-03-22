package com.wafuri.idle.application.service.player

import com.wafuri.idle.application.config.GameConfig
import com.wafuri.idle.application.exception.ResourceNotFoundException
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
  fun create(name: String): Player {
    val startingCharacterKey = "warrior"
    characterTemplateCatalog.require(startingCharacterKey)
    val player =
      Player(
        id = UUID.randomUUID(),
        name = name,
        ownedCharacterKeys = setOf(startingCharacterKey),
      )
    val savedPlayer = playerRepository.save(player)
    repeat(gameConfig.team().initialSlots().coerceAtLeast(0)) {
      teamRepository.save(Team(id = UUID.randomUUID(), playerId = savedPlayer.id))
    }
    return savedPlayer
  }

  fun get(playerId: UUID): Player =
    playerRepository.findById(playerId)
      ?: throw ResourceNotFoundException("Player $playerId was not found.")
}
