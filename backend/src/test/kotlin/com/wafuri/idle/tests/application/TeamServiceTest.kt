package com.wafuri.idle.tests.application

import com.wafuri.idle.application.exception.ValidationException
import com.wafuri.idle.application.port.out.PlayerStateWorkQueue
import com.wafuri.idle.application.port.out.Repository
import com.wafuri.idle.application.port.out.TeamRepository
import com.wafuri.idle.application.service.character.CharacterTemplateCatalog
import com.wafuri.idle.application.service.combat.CombatStatService
import com.wafuri.idle.application.service.team.TeamService
import com.wafuri.idle.domain.model.Player
import com.wafuri.idle.domain.model.Team
import com.wafuri.idle.domain.model.TeamMemberSlot
import com.wafuri.idle.tests.support.expectedPlayer
import com.wafuri.idle.tests.support.expectedTeam
import com.wafuri.idle.tests.support.warriorTemplate
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import java.util.UUID

class TeamServiceTest : StringSpec() {
  private lateinit var playerRepository: Repository<Player, UUID>
  private lateinit var teamRepository: TeamRepository
  private lateinit var characterTemplateCatalog: CharacterTemplateCatalog
  private lateinit var playerStateWorkQueue: PlayerStateWorkQueue
  private lateinit var combatStatService: CombatStatService
  private lateinit var teamSlot: CapturingSlot<Team>
  private lateinit var service: TeamService

  init {
    beforeTest {
      playerRepository = mockk()
      teamRepository = mockk()
      characterTemplateCatalog = mockk()
      playerStateWorkQueue = mockk()
      combatStatService = mockk()
      teamSlot = slot()
      service = TeamService(playerRepository, teamRepository, characterTemplateCatalog, playerStateWorkQueue, combatStatService)
    }

    "create team for player" {
      val player = Player(UUID.randomUUID(), "Alice")

      every { playerRepository.findById(player.id) } returns player
      every { teamRepository.save(capture(teamSlot)) } answers { teamSlot.captured }
      every { playerStateWorkQueue.markDirty(player.id) } just runs

      val team = service.create(player.id)

      team shouldBe expectedTeam(team.id, player.id)
      verify(exactly = 1) { teamRepository.save(any()) }
      verify(exactly = 1) { playerStateWorkQueue.markDirty(player.id) }
    }

    "activate team requires at least one character" {
      val player = Player(UUID.randomUUID(), "Alice")
      val team = Team(UUID.randomUUID(), player.id)

      every { teamRepository.findById(team.id) } returns team
      every { playerRepository.findById(player.id) } returns player

      shouldThrow<ValidationException> {
        service.activate(player.id, team.id)
      }
    }

    "assign character to team rejects duplicates across slots" {
      val player = Player(UUID.randomUUID(), "Alice", ownedCharacterKeys = setOf("warrior"))
      val team =
        Team(
          UUID.randomUUID(),
          player.id,
          slots =
            listOf(
              TeamMemberSlot(1, "warrior"),
              TeamMemberSlot(2, "character-1"),
              TeamMemberSlot(3),
            ),
        )
      val characterKey = "warrior"

      every { playerRepository.findById(player.id) } returns player
      every { teamRepository.findById(team.id) } returns team
      every { characterTemplateCatalog.require(characterKey) } returns warriorTemplate(characterKey)

      shouldThrow<ValidationException> {
        service.assignCharacter(player.id, team.id, 3, characterKey)
      }
    }

    "assign character to team adds the character key" {
      val player = Player(UUID.randomUUID(), "Alice", ownedCharacterKeys = setOf("warrior"))
      val team = Team(UUID.randomUUID(), player.id)
      val characterKey = "warrior"
      val savedTeams = mutableListOf<Team>()

      every { playerRepository.findById(player.id) } returns player
      every { teamRepository.findById(team.id) } returns team
      every { characterTemplateCatalog.require(characterKey) } returns warriorTemplate(characterKey)
      every { teamRepository.save(any()) } answers {
        firstArg<Team>().also { savedTeams += it }
      }
      every { combatStatService.invalidatePlayer(player.id) } just runs
      every { playerStateWorkQueue.markDirty(player.id) } just runs

      val updatedTeam = service.assignCharacter(player.id, team.id, 1, characterKey)
      val expectedTeam =
        expectedTeam(
          team.id,
          player.id,
          slots = listOf(TeamMemberSlot(1, "warrior"), TeamMemberSlot(2), TeamMemberSlot(3)),
        )

      updatedTeam shouldBe expectedTeam
      savedTeams.last() shouldBe expectedTeam
      verify(exactly = 1) { combatStatService.invalidatePlayer(player.id) }
      verify(exactly = 1) { playerStateWorkQueue.markDirty(player.id) }
    }

    "activate team sets player's active team" {
      val player = Player(UUID.randomUUID(), "Alice", ownedCharacterKeys = setOf("warrior"))
      val team =
        Team(
          UUID.randomUUID(),
          player.id,
          slots = listOf(TeamMemberSlot(1, "warrior"), TeamMemberSlot(2), TeamMemberSlot(3)),
        )
      val updatedPlayers = mutableListOf<Player>()

      every { teamRepository.findById(team.id) } returns team
      every { playerRepository.findById(player.id) } returns player
      every { playerRepository.save(any()) } answers { firstArg<Player>().also { updatedPlayers += it } }
      every { combatStatService.invalidatePlayer(player.id) } just runs
      every { playerStateWorkQueue.markDirty(player.id) } just runs

      val activatedTeam = service.activate(player.id, team.id)
      val expectedPlayer = expectedPlayer(player.id, "Alice", ownedCharacterKeys = setOf("warrior"), activeTeamId = team.id)

      activatedTeam shouldBe team
      updatedPlayers.last() shouldBe expectedPlayer
      verify(exactly = 1) { combatStatService.invalidatePlayer(player.id) }
      verify(exactly = 1) { playerStateWorkQueue.markDirty(player.id) }
    }
  }
}
