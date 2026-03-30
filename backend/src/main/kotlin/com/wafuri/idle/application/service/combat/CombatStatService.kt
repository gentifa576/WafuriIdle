package com.wafuri.idle.application.service.combat

import com.wafuri.idle.application.exception.ResourceNotFoundException
import com.wafuri.idle.application.exception.ValidationException
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

@ApplicationScoped
class CombatStatService(
  private val playerRepository: Repository<Player, UUID>,
  private val teamRepository: TeamRepository,
  private val inventoryRepository: InventoryRepository,
  private val characterTemplateCatalog: CharacterTemplateCatalog,
  private val combatPassiveService: CombatPassiveService,
  private val scalingRule: ScalingRule,
) {
  fun teamStatsForPlayer(playerId: UUID): TeamCombatStats = teamStatsForPlayer(playerId, emptyList())

  fun teamStatsForPlayer(
    playerId: UUID,
    existingMembers: List<CombatMemberState>,
  ): TeamCombatStats {
    val player =
      playerRepository.findById(playerId)
        ?: throw ResourceNotFoundException("Player $playerId was not found.")
    val activeTeamId =
      player.activeTeamId
        ?: throw ValidationException("Player does not have an active team.")
    return teamStats(activeTeamId, existingMembers)
  }

  fun teamStats(teamId: UUID): TeamCombatStats = teamStats(teamId, emptyList())

  fun teamStats(
    teamId: UUID,
    existingMembers: List<CombatMemberState>,
  ): TeamCombatStats {
    val team =
      teamRepository.findById(teamId)
        ?: throw ResourceNotFoundException("Team $teamId was not found.")
    if (team.characterKeys.isEmpty()) {
      throw ValidationException("Team must contain at least one character to enter combat.")
    }
    val player =
      playerRepository.findById(team.playerId)
        ?: throw ResourceNotFoundException("Player ${team.playerId} was not found.")

    val baseCharacterStats =
      team.slots.mapNotNull { teamSlot ->
        val characterKey = teamSlot.characterKey ?: return@mapNotNull null
        if (!player.ownedCharacterKeys.contains(characterKey)) {
          throw ValidationException("Team contains a character the player does not own.")
        }
        val template = characterTemplateCatalog.require(characterKey)
        val equippedItems =
          listOfNotNull(
            teamSlot.weaponItemId?.let { inventoryRepository.findById(it) },
            teamSlot.armorItemId?.let { inventoryRepository.findById(it) },
            teamSlot.accessoryItemId?.let { inventoryRepository.findById(it) },
          ).onEach { inventoryItem ->
            if (
              inventoryItem.playerId != player.id ||
              inventoryItem.equippedTeamId != team.id ||
              inventoryItem.equippedPosition != teamSlot.position
            ) {
              throw ValidationException("Team references an item that is not assigned to this team slot.")
            }
          }
        CharacterCombatStats(
          characterKey = characterKey,
          attack = template.strength.atLevel(player.level) + equippedItems.sumOf { attackBonus(it).toDouble() }.toFloat(),
          hit = template.agility.atLevel(player.level) + equippedItems.sumOf { hitBonus(it).toDouble() }.toFloat(),
          maxHp = template.vitality.atLevel(player.level) + equippedItems.sumOf { hpBonus(it).toDouble() }.toFloat(),
        )
      }
    val resolvedCharacterStats =
      combatPassiveService.applyLeaderPassive(
        teamCharacterKeys = team.characterKeys,
        characterStats = baseCharacterStats,
        existingMembers = existingMembers,
      )

    return TeamCombatStats(
      teamId = team.id,
      characterStats = resolvedCharacterStats,
    )
  }

  private fun attackBonus(inventoryItem: InventoryItem): Float = statBonus(inventoryItem, StatType.STRENGTH)

  private fun hitBonus(inventoryItem: InventoryItem): Float = statBonus(inventoryItem, StatType.AGILITY)

  private fun hpBonus(inventoryItem: InventoryItem): Float = statBonus(inventoryItem, StatType.VITALITY)

  private fun statBonus(
    inventoryItem: InventoryItem,
    statType: StatType,
  ): Float =
    listOf(scalingRule.scaledBaseStat(inventoryItem), *scalingRule.scaledSubStats(inventoryItem).toTypedArray())
      .filter { it.type == statType }
      .sumOf { it.value.toDouble() }
      .toFloat()
}
