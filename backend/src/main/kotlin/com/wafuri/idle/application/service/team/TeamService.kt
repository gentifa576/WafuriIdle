package com.wafuri.idle.application.service.team

import com.wafuri.idle.application.exception.ValidationException
import com.wafuri.idle.application.port.out.CombatStateRepository
import com.wafuri.idle.application.port.out.InventoryRepository
import com.wafuri.idle.application.port.out.PlayerStateWorkQueue
import com.wafuri.idle.application.port.out.Repository
import com.wafuri.idle.application.port.out.TeamRepository
import com.wafuri.idle.application.service.character.CharacterTemplateCatalog
import com.wafuri.idle.application.service.combat.CombatStatService
import com.wafuri.idle.domain.model.CharacterTemplate
import com.wafuri.idle.domain.model.CombatStatus
import com.wafuri.idle.domain.model.DomainRuleViolationException
import com.wafuri.idle.domain.model.EquipmentSlot
import com.wafuri.idle.domain.model.Player
import com.wafuri.idle.domain.model.Team
import com.wafuri.idle.domain.model.TeamMemberSlot
import com.wafuri.idle.transport.rest.dto.TeamSlotLoadoutRequest
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import java.util.UUID

@ApplicationScoped
class TeamService(
  private val playerRepository: Repository<Player, UUID>,
  private val teamRepository: TeamRepository,
  private val inventoryRepository: InventoryRepository,
  private val characterTemplateCatalog: CharacterTemplateCatalog,
  private val playerStateWorkQueue: PlayerStateWorkQueue,
  private val combatStatService: CombatStatService,
  private val combatStateRepository: CombatStateRepository,
) {
  @Transactional
  fun create(playerId: UUID): Team {
    playerRepository.require(playerId)
    val team = Team(UUID.randomUUID(), playerId)
    val saved = teamRepository.save(team)
    playerStateWorkQueue.markDirty(playerId)
    return saved
  }

  fun listByPlayer(playerId: UUID): List<Team> = teamRepository.findByPlayerId(playerId)

  @Transactional
  fun assignCharacter(
    teamId: UUID,
    position: Int,
    characterKey: String,
  ): Team {
    val team = teamRepository.require(teamId)
    val player = playerRepository.require(team.playerId)
    requirePlayerNotDowned(team.playerId)
    if (!player.ownedCharacterKeys.contains(characterKey)) {
      throw ValidationException("Character $characterKey is not owned by the player.")
    }
    characterTemplateCatalog.require(characterKey)

    val updatedTeam =
      try {
        team.assignCharacter(position, characterKey)
      } catch (exception: DomainRuleViolationException) {
        throw ValidationException(exception.message ?: "Character validation failed.", exception)
      }

    val savedTeam = teamRepository.save(updatedTeam)
    combatStatService.invalidatePlayer(team.playerId)
    playerStateWorkQueue.markDirty(team.playerId)
    return savedTeam
  }

  fun templates(): List<CharacterTemplate> = characterTemplateCatalog.all()

  @Transactional
  fun saveLoadout(
    teamId: UUID,
    slots: List<TeamSlotLoadoutRequest>,
  ): Team {
    val team = teamRepository.require(teamId)
    val player = playerRepository.require(team.playerId)
    requirePlayerNotDowned(team.playerId)
    requireValidLoadoutRequest(slots)

    val updatedSlots =
      slots.sortedBy { it.position }.map { request ->
        if (request.characterKey == null && hasAnyEquipment(request)) {
          throw ValidationException("Items cannot be equipped to an empty team slot.")
        }
        if (request.characterKey != null) {
          if (!player.ownedCharacterKeys.contains(request.characterKey)) {
            throw ValidationException("Character ${request.characterKey} is not owned by the player.")
          }
          characterTemplateCatalog.require(request.characterKey)
        }
        TeamMemberSlot(
          position = request.position,
          characterKey = request.characterKey,
          weaponItemId = request.weaponItemId,
          armorItemId = request.armorItemId,
          accessoryItemId = request.accessoryItemId,
        )
      }

    val updatedTeam =
      try {
        team.copy(slots = updatedSlots)
      } catch (exception: IllegalArgumentException) {
        throw ValidationException(exception.message ?: "Team validation failed.", exception)
      }

    val equipmentAssignments =
      slots.flatMap { request ->
        listOfNotNull(
          request.weaponItemId?.let { EquipmentAssignment(it, request.position, EquipmentSlot.WEAPON) },
          request.armorItemId?.let { EquipmentAssignment(it, request.position, EquipmentSlot.ARMOR) },
          request.accessoryItemId?.let { EquipmentAssignment(it, request.position, EquipmentSlot.ACCESSORY) },
        )
      }
    val uniqueAssignedItemIds = equipmentAssignments.map { it.itemId }.toSet()
    if (uniqueAssignedItemIds.size != equipmentAssignments.size) {
      throw ValidationException("Duplicate equips are forbidden.")
    }

    val inventoryById = inventoryRepository.findByPlayerId(player.id).associateBy { it.id }
    val previouslyEquippedOnTeam = inventoryById.values.filter { it.equippedTeamId == team.id }
    val updatedInventoryById =
      inventoryById
        .mapValues { (_, item) ->
          if (item.equippedTeamId == team.id) {
            item.copy(equippedTeamId = null, equippedPosition = null)
          } else {
            item
          }
        }.toMutableMap()

    for (assignment in equipmentAssignments) {
      val item =
        updatedInventoryById[assignment.itemId]
          ?: throw ValidationException("Inventory item ${assignment.itemId} was not found.")
      if (item.playerId != player.id) {
        throw ValidationException("Items must belong to the player's inventory.")
      }
      if (item.item.type != assignment.slot.allowedType) {
        throw ValidationException("Item type must match equipment slot.")
      }
      if (item.equippedTeamId != null && item.equippedTeamId != team.id) {
        throw ValidationException("Items cannot be equipped twice.")
      }
      val updatedItem =
        try {
          item.equip(player.id, team.id, assignment.position, assignment.slot)
        } catch (exception: DomainRuleViolationException) {
          throw ValidationException(exception.message ?: "Equipment validation failed.", exception)
        }
      updatedInventoryById[assignment.itemId] = updatedItem
    }

    val clearedInventory =
      previouslyEquippedOnTeam.mapNotNull { original ->
        val updated = updatedInventoryById.getValue(original.id)
        if (updated != original) updated else null
      }
    val equippedInventory =
      equipmentAssignments.mapNotNull { assignment ->
        val original = inventoryById.getValue(assignment.itemId)
        val updated = updatedInventoryById.getValue(assignment.itemId)
        if (updated != original) updated else null
      }
    val inventoryToSave = clearedInventory + equippedInventory

    val savedTeam = teamRepository.save(updatedTeam)
    for (inventoryItem in inventoryToSave.distinctBy { it.id }) {
      inventoryRepository.save(inventoryItem)
    }
    combatStatService.invalidatePlayer(team.playerId)
    playerStateWorkQueue.markDirty(team.playerId)
    return savedTeam
  }

  @Transactional
  fun activate(teamId: UUID): Team {
    val team = teamRepository.require(teamId)
    val player = playerRepository.require(team.playerId)
    requirePlayerNotDowned(team.playerId)
    if (team.characterKeys.isEmpty()) {
      throw ValidationException("Team must have at least one character before it can be activated.")
    }
    if (team.characterKeys.any { !player.ownedCharacterKeys.contains(it) }) {
      throw ValidationException("Team contains a character the player does not own.")
    }

    playerRepository.save(player.activateTeam(team.id))
    combatStatService.invalidatePlayer(team.playerId)
    playerStateWorkQueue.markDirty(team.playerId)
    return team
  }

  private fun requirePlayerNotDowned(playerId: UUID) {
    if (combatStateRepository.findById(playerId)?.status == CombatStatus.DOWN) {
      throw ValidationException("Team changes are unavailable while the player's combat is downed.")
    }
  }

  private fun requireValidLoadoutRequest(slots: List<TeamSlotLoadoutRequest>) {
    if (slots.size != Team.MAX_SIZE) {
      throw ValidationException("Team loadout must include exactly ${Team.MAX_SIZE} slots.")
    }
    if (slots.map { it.position }.sorted() != (1..Team.MAX_SIZE).toList()) {
      throw ValidationException("Team slot positions must be exactly 1 through ${Team.MAX_SIZE}.")
    }
  }

  private fun hasAnyEquipment(slot: TeamSlotLoadoutRequest): Boolean =
    slot.weaponItemId != null || slot.armorItemId != null || slot.accessoryItemId != null

  private data class EquipmentAssignment(
    val itemId: UUID,
    val position: Int,
    val slot: EquipmentSlot,
  )
}
