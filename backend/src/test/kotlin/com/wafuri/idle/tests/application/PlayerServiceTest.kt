package com.wafuri.idle.tests.application

import com.wafuri.idle.application.config.GameConfig
import com.wafuri.idle.application.port.out.Repository
import com.wafuri.idle.application.port.out.TeamRepository
import com.wafuri.idle.application.service.character.CharacterTemplateCatalog
import com.wafuri.idle.application.service.player.PlayerService
import com.wafuri.idle.domain.model.Player
import com.wafuri.idle.domain.model.Team
import com.wafuri.idle.tests.support.gameConfig
import com.wafuri.idle.tests.support.warriorTemplate
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.util.UUID

class PlayerServiceTest : StringSpec() {
  private lateinit var playerRepository: Repository<Player, UUID>
  private lateinit var teamRepository: TeamRepository
  private lateinit var characterTemplateCatalog: CharacterTemplateCatalog
  private lateinit var config: GameConfig
  private lateinit var playerSlot: CapturingSlot<Player>
  private lateinit var teamSlot: CapturingSlot<Team>
  private lateinit var service: PlayerService

  init {
    beforeTest {
      playerRepository = mockk()
      teamRepository = mockk()
      characterTemplateCatalog = mockk()
      config = gameConfig(initialTeamSlots = 3)
      playerSlot = slot()
      teamSlot = slot()
      service = PlayerService(playerRepository, teamRepository, characterTemplateCatalog, config)
    }

    "create and get player" {
      every { playerRepository.save(capture(playerSlot)) } answers { playerSlot.captured }
      every { playerRepository.findById(any()) } answers {
        playerSlot.captured.takeIf { it.id == firstArg<UUID>() }
      }
      every { teamRepository.save(capture(teamSlot)) } answers { teamSlot.captured }
      every { characterTemplateCatalog.require("warrior") } returns warriorTemplate()

      val player = service.create("Alice")

      service.get(player.id) shouldBe player
      player.ownedCharacterKeys shouldBe setOf("warrior")
      player.activeTeamId shouldBe null
      verify(exactly = 1) { playerRepository.save(any()) }
      verify(exactly = 3) { teamRepository.save(any()) }
      verify(exactly = 1) { playerRepository.findById(player.id) }
    }
  }
}
