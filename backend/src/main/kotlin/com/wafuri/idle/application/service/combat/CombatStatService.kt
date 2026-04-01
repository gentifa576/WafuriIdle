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
    val cached =
      cachedPlayerBaseStats.computeIfAbsent(playerId) {
        loadPlayerCombatBaseStats(playerId)
      }
    val resolvedCharacterStats =
      combatPassiveService.applyLeaderPassive(
        cached.teamCharacterKeys,
        cached.baseCharacterStats,
        existingMembers,
      )
    return TeamCombatStats(cached.teamId, resolvedCharacterStats)
  }

  fun invalidatePlayer(playerId: UUID) {
    cachedPlayerBaseStats.remove(playerId)
  }

  private fun loadPlayerCombatBaseStats(playerId: UUID): PlayerCombatBaseStats {
    val player =
      playerRepository.findById(playerId)
        ?: throw ResourceNotFoundException("Player $playerId was not found.")
    val activeTeamId =
      player.activeTeamId
        ?: throw ValidationException("Player does not have an active team.")
    val team =
      teamRepository.findById(activeTeamId)
        ?: throw ResourceNotFoundException("Team $activeTeamId was not found.")
    if (team.characterKeys.isEmpty()) {
      throw ValidationException("Team must contain at least one character to enter combat.")
    }

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
          characterKey,
          template.strength.atLevel(player.level) + equippedItems.sumOf { statBonus(it, StatType.STRENGTH).toDouble() }.toFloat(),
          template.agility.atLevel(player.level) + equippedItems.sumOf { statBonus(it, StatType.AGILITY).toDouble() }.toFloat(),
          template.vitality.atLevel(player.level) + equippedItems.sumOf { statBonus(it, StatType.VITALITY).toDouble() }.toFloat(),
        )
      }

    return PlayerCombatBaseStats(team.id, team.characterKeys, baseCharacterStats)
  }

  private fun statBonus(
    inventoryItem: InventoryItem,
    statType: StatType,
  ): Float =
    listOf(scalingRule.scaledBaseStat(inventoryItem), *scalingRule.scaledSubStats(inventoryItem).toTypedArray())
      .filter { it.type == statType }
      .sumOf { it.value.toDouble() }
      .toFloat()

  private data class PlayerCombatBaseStats(
    val teamId: UUID,
    val teamCharacterKeys: List<String>,
    val baseCharacterStats: List<CharacterCombatStats>,
  )
}
