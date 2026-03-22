package com.wafuri.idle.domain.model

import java.util.UUID

data class Player(
  val id: UUID,
  val name: String,
  val ownedCharacterKeys: Set<String> = emptySet(),
  val activeTeamId: UUID? = null,
) {
  init {
    require(name.isNotBlank()) { "Player name must not be blank." }
    require(ownedCharacterKeys.none { it.isBlank() }) { "Owned character keys must not be blank." }
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
}
