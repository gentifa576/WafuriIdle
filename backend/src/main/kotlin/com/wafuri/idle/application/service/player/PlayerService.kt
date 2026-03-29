package com.wafuri.idle.application.service.player

import com.wafuri.idle.application.config.GameConfig
import com.wafuri.idle.application.exception.ResourceNotFoundException
import com.wafuri.idle.application.exception.ValidationException
import com.wafuri.idle.application.model.CharacterPullResult
import com.wafuri.idle.application.port.out.PlayerStateWorkQueue
import com.wafuri.idle.application.port.out.Repository
import com.wafuri.idle.application.port.out.TeamRepository
import com.wafuri.idle.application.service.character.CharacterTemplateCatalog
import com.wafuri.idle.application.service.combat.RandomSource
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
  private val playerStateWorkQueue: PlayerStateWorkQueue,
  private val randomSource: RandomSource,
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
    return playerRepository.save(player.grantCharacter(characterKey)).also {
      playerStateWorkQueue.markDirty(playerId)
    }
  }

  @Transactional
  fun pullCharacter(playerId: UUID): CharacterPullResult {
    val player = get(playerId)
    val pullConfig = gameConfig.gacha().characterPull()
    val goldCost = pullConfig.goldCost()
    if (player.gold < goldCost) {
      throw ValidationException("Player $playerId does not have enough gold for a character pull.")
    }

    val templates = characterTemplateCatalog.all()
    if (templates.isEmpty()) {
      throw ValidationException("Character gacha is unavailable because no character templates are loaded.")
    }

    val pulledCharacterKey = templates[randomSource.nextInt(templates.size)].key
    val updatedPlayer =
      if (player.ownedCharacterKeys.contains(pulledCharacterKey)) {
        player
          .spendGold(goldCost)
          .grantEssence(pullConfig.duplicateEssence())
      } else {
        player
          .spendGold(goldCost)
          .grantCharacter(pulledCharacterKey)
      }
    val savedPlayer = playerRepository.save(updatedPlayer)
    playerStateWorkQueue.markDirty(playerId)

    val grantedCharacterKey = pulledCharacterKey.takeUnless { player.ownedCharacterKeys.contains(it) }
    val essenceGranted = if (grantedCharacterKey == null) pullConfig.duplicateEssence() else 0
    return CharacterPullResult(
      player = savedPlayer,
      pulledCharacterKey = pulledCharacterKey,
      grantedCharacterKey = grantedCharacterKey,
      essenceGranted = essenceGranted,
    )
  }

  fun get(playerId: UUID): Player =
    playerRepository.findById(playerId)
      ?: throw ResourceNotFoundException("Player $playerId was not found.")
}
