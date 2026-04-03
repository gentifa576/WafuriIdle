package com.wafuri.idle.application.service.player

import com.wafuri.idle.application.config.GameConfig
import com.wafuri.idle.application.exception.ValidationException
import com.wafuri.idle.application.model.CharacterPull
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
        UUID.randomUUID(),
        name,
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
  fun pullCharacter(
    playerId: UUID,
    count: Int = 1,
  ): CharacterPullResult {
    if (count !in setOf(1, 10)) {
      throw ValidationException("Character pull count must be 1 or 10.")
    }

    val player = get(playerId)
    val pullConfig = gameConfig.gacha().characterPull()
    val goldCost = pullConfig.goldCost()
    val totalGoldCost = goldCost * count
    if (player.gold < totalGoldCost) {
      val pullLabel = if (count == 1) "a character pull" else "$count character pulls"
      throw ValidationException("Player $playerId does not have enough gold for $pullLabel.")
    }

    val templates = characterTemplateCatalog.all()
    if (templates.isEmpty()) {
      throw ValidationException("Character gacha is unavailable because no character templates are loaded.")
    }

    var updatedPlayer = player
    val pulls =
      buildList(count) {
        repeat(count) {
          val pulledCharacterKey = templates[randomSource.nextInt(templates.size)].key
          val grantedCharacterKey = pulledCharacterKey.takeUnless { updatedPlayer.ownedCharacterKeys.contains(it) }
          val essenceGranted = if (grantedCharacterKey == null) pullConfig.duplicateEssence() else 0

          updatedPlayer =
            if (grantedCharacterKey == null) {
              updatedPlayer
                .spendGold(goldCost)
                .grantEssence(essenceGranted)
            } else {
              updatedPlayer
                .spendGold(goldCost)
                .grantCharacter(grantedCharacterKey)
            }

          add(
            CharacterPull(
              pulledCharacterKey = pulledCharacterKey,
              grantedCharacterKey = grantedCharacterKey,
              essenceGranted = essenceGranted,
            ),
          )
        }
      }

    val savedPlayer = playerRepository.save(updatedPlayer)
    playerStateWorkQueue.markDirty(playerId)

    return CharacterPullResult(
      player = savedPlayer,
      count = count,
      pulls = pulls,
      totalEssenceGranted = pulls.sumOf { it.essenceGranted },
    )
  }

  fun get(playerId: UUID): Player = playerRepository.require(playerId)
}
