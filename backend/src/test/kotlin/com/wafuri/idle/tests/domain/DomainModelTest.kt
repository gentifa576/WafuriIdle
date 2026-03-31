package com.wafuri.idle.tests.domain

import com.wafuri.idle.domain.model.CombatMemberState
import com.wafuri.idle.domain.model.CombatState
import com.wafuri.idle.domain.model.CombatStatus
import com.wafuri.idle.domain.model.DomainRuleViolationException
import com.wafuri.idle.domain.model.EquipmentSlot
import com.wafuri.idle.domain.model.InventoryItem
import com.wafuri.idle.domain.model.LevelRange
import com.wafuri.idle.domain.model.Player
import com.wafuri.idle.domain.model.Team
import com.wafuri.idle.domain.model.TeamMemberSlot
import com.wafuri.idle.domain.model.ZoneLootEntry
import com.wafuri.idle.domain.model.ZoneTemplate
import com.wafuri.idle.tests.support.armorItem
import com.wafuri.idle.tests.support.expectedCombatMemberState
import com.wafuri.idle.tests.support.expectedCombatState
import com.wafuri.idle.tests.support.expectedPlayer
import com.wafuri.idle.tests.support.swordItem
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.util.UUID

class DomainModelTest :
  StringSpec({
    "team rejects duplicate characters across slots" {
      val team =
        Team(
          id = UUID.randomUUID(),
          playerId = UUID.randomUUID(),
          slots =
            listOf(
              TeamMemberSlot(position = 1, characterKey = "warrior"),
              TeamMemberSlot(position = 2, characterKey = "cleric"),
              TeamMemberSlot(position = 3),
            ),
        )

      shouldThrow<DomainRuleViolationException> {
        team.assignCharacter(3, "warrior")
      }
    }

    "item type must match slot" {
      val playerId = UUID.randomUUID()
      val inventoryItem =
        InventoryItem(
          id = UUID.randomUUID(),
          playerId = playerId,
          item = armorItem(),
        )

      shouldThrow<DomainRuleViolationException> {
        inventoryItem.equip(playerId, UUID.randomUUID(), 1, EquipmentSlot.WEAPON)
      }
    }

    "items must belong to player inventory" {
      val inventoryItem =
        InventoryItem(
          id = UUID.randomUUID(),
          playerId = UUID.randomUUID(),
          item = swordItem(),
        )

      shouldThrow<DomainRuleViolationException> {
        inventoryItem.equip(UUID.randomUUID(), UUID.randomUUID(), 1, EquipmentSlot.WEAPON)
      }
    }

    "item cannot be equipped twice" {
      val playerId = UUID.randomUUID()
      val inventoryItem =
        InventoryItem(
          id = UUID.randomUUID(),
          playerId = playerId,
          item = swordItem(),
          equippedTeamId = UUID.randomUUID(),
          equippedPosition = 1,
        )

      shouldThrow<DomainRuleViolationException> {
        inventoryItem.equip(playerId, UUID.randomUUID(), 1, EquipmentSlot.WEAPON)
      }
    }

    "unequip returns item to inventory" {
      val playerId = UUID.randomUUID()
      val itemId = UUID.randomUUID()
      val teamId = UUID.randomUUID()
      val inventoryItem =
        InventoryItem(
          id = itemId,
          playerId = playerId,
          item = swordItem(),
          equippedTeamId = teamId,
          equippedPosition = 1,
        )

      val updatedItem = inventoryItem.unequip(teamId, 1, EquipmentSlot.WEAPON)

      updatedItem.equippedTeamId.shouldBeNull()
      updatedItem.equippedPosition.shouldBeNull()
    }

    "player grants unique character keys only once" {
      val player = Player(UUID.randomUUID(), "Alice", ownedCharacterKeys = setOf("warrior"))

      val updated = player.grantCharacter("cleric").grantCharacter("cleric")

      updated.ownedCharacterKeys shouldBe setOf("warrior", "cleric")
    }

    "player currencies support rewards spending and duplicate compensation" {
      val player = Player(UUID.randomUUID(), "Alice", gold = 100, essence = 3)

      val updated =
        player
          .grantGold(25)
          .spendGold(40)
          .grantEssence(15)

      updated shouldBe expectedPlayer(id = player.id, name = "Alice", gold = 85, essence = 18)
    }

    "combat state advances enemy hp only after enough accumulated elapsed time" {
      val state =
        CombatState(
          playerId = UUID.randomUUID(),
          status = CombatStatus.FIGHTING,
          zoneId = "starter-plains",
          activeTeamId = UUID.randomUUID(),
          enemyName = "Training Dummy",
          enemyLevel = 1,
          enemyBaseHp = 10f,
          enemyAttack = 1f,
          enemyHp = 10f,
          enemyMaxHp = 10f,
          members =
            listOf(
              CombatMemberState(
                characterKey = "warrior",
                attack = 10f,
                hit = 3f,
                currentHp = 15f,
                maxHp = 15f,
              ),
            ),
        )

      val buffered =
        state.advance(500L, 1000L, 1000L, 30_000L, 0.5f)
      val updated =
        buffered.advance(500L, 1000L, 1000L, 30_000L, 0.5f)

      buffered shouldBe
        expectedCombatState(
          playerId = state.playerId,
          status = CombatStatus.FIGHTING,
          zoneId = "starter-plains",
          activeTeamId = state.activeTeamId,
          enemyName = "Training Dummy",
          enemyHp = 10f,
          enemyMaxHp = 10f,
          members = listOf(expectedCombatMemberState("warrior", 10f, 3f, 15f, 15f)),
          pendingDamageMillis = 500L,
        )
      updated shouldBe
        expectedCombatState(
          playerId = state.playerId,
          status = CombatStatus.WON,
          zoneId = "starter-plains",
          activeTeamId = state.activeTeamId,
          enemyName = "Training Dummy",
          enemyHp = 0f,
          enemyMaxHp = 10f,
          members = listOf(expectedCombatMemberState("warrior", 10f, 3f, 14f, 15f)),
        )
    }

    "combat state respawns after the configured delay and consumes overflow time" {
      val state =
        CombatState(
          playerId = UUID.randomUUID(),
          status = CombatStatus.WON,
          zoneId = "starter-plains",
          activeTeamId = UUID.randomUUID(),
          enemyName = "Training Dummy",
          enemyLevel = 1,
          enemyBaseHp = 10f,
          enemyAttack = 1f,
          enemyHp = 0f,
          enemyMaxHp = 10f,
          members =
            listOf(
              CombatMemberState(
                characterKey = "warrior",
                attack = 10f,
                hit = 3f,
                currentHp = 15f,
                maxHp = 15f,
              ),
            ),
        )

      val updated =
        state.advance(1500L, 1000L, 1000L, 30_000L, 0.5f)

      updated shouldBe
        expectedCombatState(
          playerId = state.playerId,
          status = CombatStatus.FIGHTING,
          zoneId = "starter-plains",
          activeTeamId = state.activeTeamId,
          enemyName = "Training Dummy",
          enemyHp = 10f,
          enemyMaxHp = 10f,
          members = listOf(expectedCombatMemberState("warrior", 10f, 3f, 15f, 15f)),
          pendingDamageMillis = 500L,
        )
    }

    "zone template requires at least one enemy and positive loot weights" {
      shouldThrow<IllegalArgumentException> {
        ZoneTemplate(
          id = "starter-plains",
          name = "Starter Plains",
          levelRange = LevelRange(1, 10),
          eventRefs = emptyList(),
          lootTable = listOf(ZoneLootEntry(itemName = "sword_0001", weight = 0)),
          enemies = emptyList(),
        )
      }
    }

    "dead combat member contributes no dps" {
      val member =
        CombatMemberState(
          characterKey = "warrior",
          attack = 10f,
          hit = 3f,
          currentHp = 0f,
          maxHp = 15f,
        )

      member shouldBe expectedCombatMemberState("warrior", 10f, 3f, 0f, 15f)
    }

    "combat state enters down status on a full wipe and revives at half hp after the revive delay" {
      val state =
        CombatState(
          playerId = UUID.randomUUID(),
          status = CombatStatus.FIGHTING,
          zoneId = "starter-plains",
          activeTeamId = UUID.randomUUID(),
          enemyName = "Training Dummy",
          enemyLevel = 1,
          enemyBaseHp = 100f,
          enemyAttack = 1f,
          enemyHp = 100f,
          enemyMaxHp = 100f,
          members =
            listOf(
              CombatMemberState(
                characterKey = "warrior",
                attack = 1f,
                hit = 1f,
                currentHp = 1f,
                maxHp = 10f,
              ),
            ),
        )

      val downed =
        state.advance(1000L, 1000L, 1000L, 30_000L, 0.5f)
      val revived =
        downed.advance(30_000L, 1000L, 1000L, 30_000L, 0.5f)

      downed shouldBe
        expectedCombatState(
          playerId = state.playerId,
          status = CombatStatus.DOWN,
          zoneId = "starter-plains",
          activeTeamId = state.activeTeamId,
          enemyName = "Training Dummy",
          enemyHp = 99f,
          enemyMaxHp = 100f,
          members = listOf(expectedCombatMemberState("warrior", 1f, 1f, 0f, 10f)),
        )
      revived shouldBe
        expectedCombatState(
          playerId = state.playerId,
          status = CombatStatus.FIGHTING,
          zoneId = "starter-plains",
          activeTeamId = state.activeTeamId,
          enemyName = "Training Dummy",
          enemyHp = 100f,
          enemyMaxHp = 100f,
          members = listOf(expectedCombatMemberState("warrior", 1f, 1f, 5f, 10f)),
        )
    }
  })
