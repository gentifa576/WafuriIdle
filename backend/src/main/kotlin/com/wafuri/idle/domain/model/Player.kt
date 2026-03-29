package com.wafuri.idle.domain.model

import java.util.UUID

data class Player(
  val id: UUID,
  val name: String,
  val ownedCharacterKeys: Set<String> = emptySet(),
  val activeTeamId: UUID? = null,
  val experience: Int = 0,
  val level: Int = 1,
  val gold: Int = 0,
  val essence: Int = 0,
) {
  init {
    require(name.isNotBlank()) { "Player name must not be blank." }
    require(ownedCharacterKeys.none { it.isBlank() }) { "Owned character keys must not be blank." }
    require(experience >= 0) { "Player experience must not be negative." }
    require(level >= 1) { "Player level must be at least 1." }
    require(gold >= 0) { "Player gold must not be negative." }
    require(essence >= 0) { "Player essence must not be negative." }
  }

  fun grantCharacter(characterKey: String): Player {
    require(characterKey.isNotBlank()) { "Character key must not be blank." }
    return if (ownedCharacterKeys.contains(characterKey)) {
      this
    } else {
      copy(ownedCharacterKeys = ownedCharacterKeys + characterKey)
    }
  }

  fun activateTeam(teamId: UUID): Player = copy(activeTeamId = teamId)

  fun grantGold(amount: Int): Player {
    require(amount >= 0) { "Granted gold must not be negative." }
    return copy(gold = gold + amount)
  }

  fun spendGold(amount: Int): Player {
    require(amount >= 0) { "Spent gold must not be negative." }
    require(gold >= amount) { "Player gold must be sufficient to spend." }
    return copy(gold = gold - amount)
  }

  fun grantEssence(amount: Int): Player {
    require(amount >= 0) { "Granted essence must not be negative." }
    return copy(essence = essence + amount)
  }

  fun grantExperience(
    amount: Int,
    experiencePerLevel: Int,
  ): Player {
    require(amount >= 0) { "Granted experience must not be negative." }
    require(experiencePerLevel > 0) { "Experience per level must be positive." }
    val nextExperience = experience + amount
    val nextLevel = (nextExperience / experiencePerLevel) + 1
    return copy(experience = nextExperience, level = nextLevel)
  }
}
