package com.wafuri.idle.application.service.combat

import com.wafuri.idle.application.model.CharacterCombatStats
import com.wafuri.idle.application.service.character.CharacterTemplateCatalog
import com.wafuri.idle.domain.model.CombatConditionDefinition
import com.wafuri.idle.domain.model.CombatConditionType
import com.wafuri.idle.domain.model.CombatMemberState
import com.wafuri.idle.domain.model.CombatModifierType
import com.wafuri.idle.domain.model.PassiveTriggerType
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class CombatPassiveService(
  private val characterTemplateCatalog: CharacterTemplateCatalog,
) {
  fun applyLeaderPassive(
    teamCharacterKeys: List<String>,
    characterStats: List<CharacterCombatStats>,
    existingMembers: List<CombatMemberState> = emptyList(),
  ): List<CharacterCombatStats> {
    val leaderKey = teamCharacterKeys.firstOrNull() ?: return characterStats
    val leaderTemplate = characterTemplateCatalog.require(leaderKey)
    val passive = leaderTemplate.passive ?: return characterStats
    if (passive.leaderOnly && leaderKey !in teamCharacterKeys.take(1)) {
      return characterStats
    }
    if (passive.trigger != PassiveTriggerType.AURA) {
      return characterStats
    }
    if (!conditionMatches(passive.condition, teamCharacterKeys, existingMembers)) {
      return characterStats
    }
    return passive.modifiers.fold(characterStats) { stats, modifier ->
      when (modifier.type) {
        CombatModifierType.ATTACK_MULTIPLIER ->
          stats.map { it.copy(attack = it.attack * (1f + modifier.value)) }
        CombatModifierType.OUTGOING_DAMAGE_MULTIPLIER,
        CombatModifierType.INCOMING_DAMAGE_MULTIPLIER,
        -> stats
      }
    }
  }

  private fun conditionMatches(
    condition: CombatConditionDefinition,
    teamCharacterKeys: List<String>,
    existingMembers: List<CombatMemberState>,
  ): Boolean =
    when (condition.type) {
      CombatConditionType.ALWAYS -> true
      CombatConditionType.ALIVE_ALLIES_WITH_TAG_AT_LEAST -> {
        val requiredTag = condition.tag ?: return false
        val minimumCount = condition.minimumCount ?: return false
        val aliveMembers = existingMembers.associateBy { it.characterKey }
        teamCharacterKeys.count { characterKey ->
          val isAlive = aliveMembers[characterKey]?.isAlive ?: true
          isAlive && characterTemplateCatalog.require(characterKey).tags.contains(requiredTag)
        } >= minimumCount
      }
      CombatConditionType.SELF_HP_BELOW_PERCENT,
      CombatConditionType.ANY_ALLY_HP_BELOW_PERCENT,
      -> false
    }
}
