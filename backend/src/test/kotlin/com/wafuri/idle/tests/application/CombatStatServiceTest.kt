package com.wafuri.idle.tests.application

import com.wafuri.idle.application.exception.ValidationException
import com.wafuri.idle.application.model.TeamCombatStats
import com.wafuri.idle.application.port.out.Repository
import com.wafuri.idle.application.port.out.TeamRepository
import com.wafuri.idle.application.service.character.CharacterTemplateCatalog
import com.wafuri.idle.application.service.combat.CombatStatService
import com.wafuri.idle.domain.model.Player
import com.wafuri.idle.domain.model.Team
import com.wafuri.idle.tests.support.clericTemplate
import com.wafuri.idle.tests.support.warriorTemplate
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
  private lateinit var characterTemplateCatalog: CharacterTemplateCatalog
  private lateinit var service: CombatStatService

  init {
    beforeTest {
      playerRepository = mockk()
      teamRepository = mockk()
      characterTemplateCatalog = mockk()
      service = CombatStatService(playerRepository, teamRepository, characterTemplateCatalog)
    }

    "team stats derive attack hit and hp from base template stats" {
      val player = Player(UUID.randomUUID(), "Alice", ownedCharacterKeys = setOf("warrior", "cleric"))
      val teamId = UUID.randomUUID()
      val team = Team(teamId, player.id, listOf("warrior", "cleric"))

      every { teamRepository.findById(teamId) } returns team
      every { playerRepository.findById(player.id) } returns player
      every { characterTemplateCatalog.require("warrior") } returns warriorTemplate()
      every { characterTemplateCatalog.require("cleric") } returns clericTemplate()

      val result = service.teamStats(teamId)

      result shouldBe
        TeamCombatStats(
          teamId = teamId,
          characterStats = result.characterStats,
        )
      result.characterStats[0].attack shouldBe 12f
      result.characterStats[0].hit shouldBe 7f
      result.characterStats[0].maxHp shouldBe 11f
      result.characterStats[1].attack shouldBe 5f
      result.characterStats[1].hit shouldBe 4f
      result.characterStats[1].maxHp shouldBe 8f
      result.dps shouldBe (104f plusOrMinus 0.001f)
      result.toCombatMembers().sumOf { it.currentHp.toDouble() }.toFloat() shouldBe 19f
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
