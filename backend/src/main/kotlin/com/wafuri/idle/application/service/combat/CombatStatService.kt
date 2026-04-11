package com.wafuri.idle.application.service.combat

import com.wafuri.idle.application.exception.ValidationException
import com.wafuri.idle.application.model.CharacterCombatSkillStats
import com.wafuri.idle.application.model.CharacterCombatStats
import com.wafuri.idle.application.model.TeamCombatStats
import com.wafuri.idle.application.port.out.InventoryRepository
import com.wafuri.idle.application.port.out.Repository
import com.wafuri.idle.application.port.out.TeamRepository
import com.wafuri.idle.application.service.character.CharacterTemplateCatalog
import com.wafuri.idle.application.service.scaling.ScalingRule
import com.wafuri.idle.domain.model.CombatMemberState
import com.wafuri.idle.domain.model.InventoryItem
import com.wafuri.idle.domain.model.Player
import com.wafuri.idle.domain.model.StatType
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@ApplicationScoped
class CombatStatService(
  private val playerRepository: Repository<Player, UUID>,
  private val teamRepository: TeamRepository,
  private val inventoryRepository: InventoryRepository,
  private val characterTemplateCatalog: CharacterTemplateCatalog,
  private val combatPassiveService: CombatPassiveService,
  private val scalingRule: ScalingRule,
) {
  private val cachedPlayerBaseStats = ConcurrentHashMap<UUID, PlayerCombatBaseStats>()

  fun teamStatsForPlayer(
    playerId: UUID,
    existingMembers: List<CombatMemberState> = emptyList(),
  ): TeamCombatStats {
    val player = playerRepository.require(playerId)
    val activeTeamId =
      player.activeTeamId
        ?: throw ValidationException("Player does not have an active team.")
    val team = teamRepository.require(activeTeamId)
    if (team.characterKeys.isEmpty()) {
      throw ValidationException("Team must contain at least one character to enter combat.")
    }
    return teamStatsForPlayerOrNull(playerId, existingMembers)
      ?: throw ValidationException("Team must contain at least one owned character to enter combat.")
  }

  fun teamStatsForPlayerOrNull(
    playerId: UUID,
    existingMembers: List<CombatMemberState> = emptyList(),
  ): TeamCombatStats? {
    val cached = cachedCombatBaseStats(playerId)
    if (cached == null) {
      cachedPlayerBaseStats.remove(playerId)
      return null
    }
    val resolvedCharacterStats =
      combatPassiveService.applyLeaderPassive(
        cached.teamCharacterKeys,
        cached.baseCharacterStats,
        existingMembers,
      )
    return TeamCombatStats(cached.teamId, resolvedCharacterStats)
  }

  fun teamSkillsForPlayerOrNull(playerId: UUID): Map<String, CharacterCombatSkillStats>? {
    val cached = cachedCombatBaseStats(playerId) ?: return null
    return cached.baseCharacterSkills
  }

  fun invalidatePlayer(playerId: UUID) {
    cachedPlayerBaseStats.remove(playerId)
  }

  private fun cachedCombatBaseStats(playerId: UUID): PlayerCombatBaseStats? =
    cachedPlayerBaseStats[playerId] ?: loadPlayerCombatBaseStats(playerId)?.also { cachedPlayerBaseStats[playerId] = it }

  private fun loadPlayerCombatBaseStats(playerId: UUID): PlayerCombatBaseStats? {
    val player = playerRepository.require(playerId)
    val activeTeamId = player.activeTeamId ?: return null
    val team = teamRepository.require(activeTeamId)
    val occupiedSlots = team.slots.filter { it.characterKey != null }

    val baseCharacterStats =
      occupiedSlots
        .map { teamSlot ->
          val characterKey = teamSlot.characterKey!!
          characterKey.takeIf { it in player.ownedCharacterKeys }?.let { ownedCharacterKey ->
            val template = characterTemplateCatalog.require(ownedCharacterKey)
            val equippedItems =
              listOfNotNull(
                teamSlot.weaponItemId,
                teamSlot.armorItemId,
                teamSlot.accessoryItemId,
              ).mapNotNull { inventoryRepository.findById(it) }
                .filter { inventoryItem ->
                  inventoryItem.playerId == player.id &&
                    inventoryItem.equippedTeamId == team.id &&
                    inventoryItem.equippedPosition == teamSlot.position
                }

            val totalStrength =
              scalingRule.scaledStrength(template.strength, player.level) +
                equippedStatBonus(equippedItems, StatType.STRENGTH)
            val totalAgility =
              scalingRule.scaledAgility(template.agility, player.level) +
                equippedStatBonus(equippedItems, StatType.AGILITY)
            val totalVitality =
              scalingRule.scaledVitality(template.vitality, player.level) +
                equippedStatBonus(equippedItems, StatType.VITALITY)

            CharacterCombatStats(
              ownedCharacterKey,
              scalingRule.attackForStrength(totalStrength),
              scalingRule.hitForAgility(totalAgility),
              scalingRule.hpForVitality(totalVitality),
            )
          }
        }.filterNotNull()

    val baseCharacterSkills =
      occupiedSlots
        .mapNotNull { teamSlot ->
          val characterKey = teamSlot.characterKey ?: return@mapNotNull null
          characterKey.takeIf { it in player.ownedCharacterKeys }?.let { ownedCharacterKey ->
            characterTemplateCatalog.require(ownedCharacterKey).skill?.let { skill ->
              ownedCharacterKey to
                CharacterCombatSkillStats(
                  key = skill.key,
                  cooldownMillis = skill.cooldownMillis,
                  effects = skill.effects,
                )
            }
          }
        }.toMap()

    if (baseCharacterStats.isEmpty()) {
      return null
    }

    return PlayerCombatBaseStats(
      team.id,
      baseCharacterStats.map { it.characterKey },
      baseCharacterStats,
      baseCharacterSkills,
    )
  }

  private fun statBonus(
    inventoryItem: InventoryItem,
    statType: StatType,
  ): Float {
    val scaledStats =
      listOf(
        scalingRule.scaledBaseStat(inventoryItem),
        *scalingRule.scaledSubStats(inventoryItem).toTypedArray(),
      )
    return scaledStats
      .filter { it.type == statType }
      .sumOf { it.value.toDouble() }
      .toFloat()
  }

  private fun equippedStatBonus(
    equippedItems: List<InventoryItem>,
    statType: StatType,
  ): Float = equippedItems.sumOf { statBonus(it, statType).toDouble() }.toFloat()

  private data class PlayerCombatBaseStats(
    val teamId: UUID,
    val teamCharacterKeys: List<String>,
    val baseCharacterStats: List<CharacterCombatStats>,
    val baseCharacterSkills: Map<String, CharacterCombatSkillStats>,
  )
}
