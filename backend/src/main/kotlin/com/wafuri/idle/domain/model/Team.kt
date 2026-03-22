package com.wafuri.idle.domain.model

import java.util.UUID

data class Team(
  val id: UUID,
  val playerId: UUID,
  val characterKeys: List<String> = emptyList(),
) {
  init {
    if (characterKeys.size > MAX_SIZE) {
      throw DomainRuleViolationException("Team max size is $MAX_SIZE.")
    }
    if (characterKeys.any { it.isBlank() }) {
      throw DomainRuleViolationException("Character key must not be blank.")
    }
    if (characterKeys.distinct().size != characterKeys.size) {
      throw DomainRuleViolationException("Duplicate characters are not allowed in a team.")
    }
  }

  fun addCharacter(characterKey: String): Team {
    if (characterKey.isBlank()) {
      throw DomainRuleViolationException("Character key must not be blank.")
    }
    if (characterKeys.size >= MAX_SIZE) {
      throw DomainRuleViolationException("Team max size is $MAX_SIZE.")
    }
    if (characterKeys.contains(characterKey)) {
      throw DomainRuleViolationException("Character is already on the team.")
    }
    return copy(characterKeys = characterKeys + characterKey)
  }

  companion object {
    const val MAX_SIZE = 3
  }
}
