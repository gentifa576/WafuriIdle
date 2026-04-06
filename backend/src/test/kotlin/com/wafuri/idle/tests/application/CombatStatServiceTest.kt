package com.wafuri.idle.tests.application

import com.wafuri.idle.application.exception.ValidationException
import com.wafuri.idle.application.port.out.InventoryRepository
import com.wafuri.idle.application.port.out.Repository
import com.wafuri.idle.application.port.out.TeamRepository
import com.wafuri.idle.application.service.character.CharacterTemplateCatalog
import com.wafuri.idle.application.service.combat.CombatPassiveService
import com.wafuri.idle.application.service.combat.CombatStatService
import com.wafuri.idle.application.service.scaling.ScalingRule
import com.wafuri.idle.domain.model.Player
import com.wafuri.idle.domain.model.StatGrowth
import com.wafuri.idle.domain.model.TeamMemberSlot
import com.wafuri.idle.tests.support.characterTemplate
import com.wafuri.idle.tests.support.clericTemplate
import com.wafuri.idle.tests.support.expectedCharacterCombatStats
import com.wafuri.idle.tests.support.expectedCombatMemberState
import com.wafuri.idle.tests.support.expectedPlayer
import com.wafuri.idle.tests.support.expectedTeam
import com.wafuri.idle.tests.support.expectedTeamCombatStats
import com.wafuri.idle.tests.support.gameConfig
import com.wafuri.idle.tests.support.rangerTemplate
import com.wafuri.idle.tests.support.warriorTemplate
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.UUID

class CombatStatServiceTest : StringSpec() {
  private lateinit var playerRepository: Repository<Player, UUID>
  private lateinit var teamRepository: TeamRepository
  private lateinit var inventoryRepository: InventoryRepository
  private lateinit var characterTemplateCatalog: CharacterTemplateCatalog
  private lateinit var service: CombatStatService

  init {
    beforeTest {
      playerRepository = mockk()
      teamRepository = mockk()
      inventoryRepository = mockk()
      characterTemplateCatalog = CharacterTemplateCatalog()
      service =
        CombatStatService(
          playerRepository,
          teamRepository,
          inventoryRepository,
          characterTemplateCatalog,
          CombatPassiveService(characterTemplateCatalog),
          ScalingRule(gameConfig()),
        )
      every { inventoryRepository.findById(any()) } returns null
    }

    "team stats derive attack hit and hp from template growth at player level" {
      val teamId = UUID.randomUUID()
      val player =
        expectedPlayer(
          UUID.randomUUID(),
          "Alice",
          ownedCharacterKeys = setOf("warrior", "cleric"),
          level = 3,
          activeTeamId = teamId,
        )
      val team =
        expectedTeam(
          id = teamId,
          playerId = player.id,
          slots = listOf(TeamMemberSlot(1, "warrior"), TeamMemberSlot(2, "cleric"), TeamMemberSlot(3)),
        )
      val warrior =
        characterTemplate(
          key = "warrior",
          name = "Warrior",
          strength = StatGrowth(12f, 2f),
          agility = StatGrowth(7f, 1.1f),
          intelligence = StatGrowth(4f, 0.4f),
          wisdom = StatGrowth(5f, 0.5f),
          vitality = StatGrowth(11f, 1.8f),
        )
      val cleric =
        characterTemplate(
          key = "cleric",
          name = "Cleric",
          strength = StatGrowth(5f, 0.7f),
          agility = StatGrowth(4f, 0.6f),
          intelligence = StatGrowth(10f, 1.6f),
          wisdom = StatGrowth(12f, 1.9f),
          vitality = StatGrowth(8f, 1.2f),
        )

      every { teamRepository.require(teamId) } returns team
      every { playerRepository.require(player.id) } returns player
      characterTemplateCatalog.replace(setOf(warrior, cleric))

      val result = service.teamStatsForPlayer(player.id)
      val expected =
        expectedTeamCombatStats(
          teamId,
          characterStats =
            listOf(
              expectedCharacterCombatStats("warrior", 24.627907f, 2.8531034f, 643.28906f),
              expectedCharacterCombatStats("cleric", 10.019767f, 2.1498666f, 462.1927f),
            ),
        )

      result shouldBe expected
      result.toCombatMembers() shouldBe
        listOf(
          expectedCombatMemberState("warrior", 24.627907f, 2.8531034f, 643.28906f, 643.28906f),
          expectedCombatMemberState("cleric", 10.019767f, 2.1498666f, 462.1927f, 462.1927f),
        )
    }

    "existing combat members preserve their hp ratio when max hp changes" {
      val teamId = UUID.randomUUID()
      val stats =
        expectedTeamCombatStats(
          teamId,
          listOf(expectedCharacterCombatStats("warrior", 28f, 2f, 730f)),
        )

      stats.toCombatMembers(
        listOf(expectedCombatMemberState("warrior", 14f, 8f, currentHp = 5f, maxHp = 10f)),
      ) shouldBe
        listOf(
          expectedCombatMemberState("warrior", 28f, 2f, currentHp = 365f, maxHp = 730f),
        )
    }

    "leader aura passive buffs team attack when condition matches" {
      val teamId = UUID.randomUUID()
      val player =
        expectedPlayer(
          UUID.randomUUID(),
          "Alice",
          ownedCharacterKeys = setOf("cleric", "ranger"),
          level = 2,
          activeTeamId = teamId,
        )
      val team =
        expectedTeam(
          id = teamId,
          playerId = player.id,
          slots = listOf(TeamMemberSlot(1, "cleric"), TeamMemberSlot(2, "ranger"), TeamMemberSlot(3)),
        )

      every { teamRepository.require(teamId) } returns team
      every { playerRepository.require(player.id) } returns player
      characterTemplateCatalog.replace(setOf(clericTemplate(), rangerTemplate()))

      val result = service.teamStatsForPlayer(player.id)

      result shouldBe
        expectedTeamCombatStats(
          teamId,
          characterStats =
            listOf(
              expectedCharacterCombatStats("cleric", 14.095638f, 2.0777254f, 431.69427f),
              expectedCharacterCombatStats("ranger", 22.386627f, 3.598726f, 484.33548f),
            ),
        )
    }

    "player combat stats require an active team" {
      val player = expectedPlayer(UUID.randomUUID(), "Alice")

      every { playerRepository.require(player.id) } returns player

      shouldThrow<ValidationException> {
        service.teamStatsForPlayer(player.id)
      }
    }

    "player combat stats cache base repository reads until invalidated" {
      val teamId = UUID.randomUUID()
      val player =
        expectedPlayer(UUID.randomUUID(), "Alice", ownedCharacterKeys = setOf("warrior"), level = 2, activeTeamId = teamId)
      val team =
        expectedTeam(
          id = teamId,
          playerId = player.id,
          slots = listOf(TeamMemberSlot(1, "warrior"), TeamMemberSlot(2), TeamMemberSlot(3)),
        )

      every { playerRepository.require(player.id) } returns player
      every { teamRepository.require(teamId) } returns team
      characterTemplateCatalog.replace(setOf(warriorTemplate()))

      requireNotNull(service.teamStatsForPlayerOrNull(player.id))
      requireNotNull(service.teamStatsForPlayerOrNull(player.id))

      verify(exactly = 1) { playerRepository.require(player.id) }
      verify(exactly = 1) { teamRepository.require(teamId) }

      service.invalidatePlayer(player.id)
      requireNotNull(service.teamStatsForPlayerOrNull(player.id))

      verify(exactly = 2) { playerRepository.require(player.id) }
      verify(exactly = 2) { teamRepository.require(teamId) }
    }

    "player combat stats reapply leader passive from cache using current member state" {
      val teamId = UUID.randomUUID()
      val player =
        expectedPlayer(
          UUID.randomUUID(),
          "Alice",
          ownedCharacterKeys = setOf("cleric", "ranger"),
          level = 2,
          activeTeamId = teamId,
        )
      val team =
        expectedTeam(
          id = teamId,
          playerId = player.id,
          slots = listOf(TeamMemberSlot(1, "cleric"), TeamMemberSlot(2, "ranger"), TeamMemberSlot(3)),
        )

      every { playerRepository.require(player.id) } returns player
      every { teamRepository.require(teamId) } returns team
      characterTemplateCatalog.replace(setOf(clericTemplate(), rangerTemplate()))

      val buffed = requireNotNull(service.teamStatsForPlayerOrNull(player.id))
      val unbuffed =
        requireNotNull(
          service.teamStatsForPlayerOrNull(
            player.id,
            existingMembers =
              listOf(
                expectedCombatMemberState("cleric", 5.7f, 1f, 9.2f, 9.2f),
                expectedCombatMemberState("ranger", 9f, 2f, 0f, 10.3f),
              ),
          ),
        )

      buffed.characterStats.first { it.characterKey == "cleric" }.attack shouldBe 14.095638f
      unbuffed.characterStats.first { it.characterKey == "cleric" }.attack shouldBe 9.397092f
      verify(exactly = 1) { playerRepository.require(player.id) }
      verify(exactly = 1) { teamRepository.require(teamId) }
    }
  }
}
