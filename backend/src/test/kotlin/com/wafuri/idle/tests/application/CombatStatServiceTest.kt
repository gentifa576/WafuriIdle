package com.wafuri.idle.tests.application

import com.wafuri.idle.application.exception.ValidationException
import com.wafuri.idle.application.model.TeamCombatStats
import com.wafuri.idle.application.port.out.InventoryRepository
import com.wafuri.idle.application.port.out.Repository
import com.wafuri.idle.application.port.out.TeamRepository
import com.wafuri.idle.application.service.character.CharacterTemplateCatalog
import com.wafuri.idle.application.service.combat.CombatPassiveService
import com.wafuri.idle.application.service.combat.CombatStatService
import com.wafuri.idle.domain.model.Player
import com.wafuri.idle.domain.model.StatGrowth
import com.wafuri.idle.domain.model.Team
import com.wafuri.idle.domain.model.TeamMemberSlot
import com.wafuri.idle.tests.support.characterTemplate
import com.wafuri.idle.tests.support.clericTemplate
import com.wafuri.idle.tests.support.rangerTemplate
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
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
        )
      every { inventoryRepository.findById(any()) } returns null
    }

    "team stats derive attack hit and hp from template growth at player level" {
      val player = Player(UUID.randomUUID(), "Alice", ownedCharacterKeys = setOf("warrior", "cleric"), level = 3)
      val teamId = UUID.randomUUID()
      val team =
        Team(
          teamId,
          player.id,
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

      every { teamRepository.findById(teamId) } returns team
      every { playerRepository.findById(player.id) } returns player
      characterTemplateCatalog.replace(listOf(warrior, cleric))

      val result = service.teamStats(teamId)

      result shouldBe
        TeamCombatStats(
          teamId = teamId,
          characterStats = result.characterStats,
        )
      result.characterStats[0].attack shouldBe (16f plusOrMinus 0.001f)
      result.characterStats[0].hit shouldBe (9.2f plusOrMinus 0.001f)
      result.characterStats[0].maxHp shouldBe (14.6f plusOrMinus 0.001f)
      result.characterStats[1].attack shouldBe (6.4f plusOrMinus 0.001f)
      result.characterStats[1].hit shouldBe (5.2f plusOrMinus 0.001f)
      result.characterStats[1].maxHp shouldBe (10.4f plusOrMinus 0.001f)
      result.dps shouldBe (180.48f plusOrMinus 0.001f)
      result.toCombatMembers().sumOf { it.currentHp.toDouble() }.toFloat() shouldBe (25f plusOrMinus 0.001f)
    }

    "leader aura passive buffs team attack when condition matches" {
      val player = Player(UUID.randomUUID(), "Alice", ownedCharacterKeys = setOf("cleric", "ranger"), level = 2)
      val teamId = UUID.randomUUID()
      val team =
        Team(
          teamId,
          player.id,
          slots = listOf(TeamMemberSlot(1, "cleric"), TeamMemberSlot(2, "ranger"), TeamMemberSlot(3)),
        )

      every { teamRepository.findById(teamId) } returns team
      every { playerRepository.findById(player.id) } returns player
      characterTemplateCatalog.replace(listOf(clericTemplate(), rangerTemplate()))

      val result = service.teamStats(teamId)

      result.characterStats[0].attack shouldBe (8.55f plusOrMinus 0.001f)
      result.characterStats[1].attack shouldBe (13.5f plusOrMinus 0.001f)
    }

    "player combat stats require an active team" {
      val player = Player(UUID.randomUUID(), "Alice")

      every { playerRepository.findById(player.id) } returns player

      shouldThrow<ValidationException> {
        service.teamStatsForPlayer(player.id)
      }
    }
  }
}
