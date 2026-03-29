package com.wafuri.idle.tests.application

import com.wafuri.idle.application.config.GameConfig
import com.wafuri.idle.application.port.out.PlayerStateWorkQueue
import com.wafuri.idle.application.port.out.Repository
import com.wafuri.idle.application.port.out.TeamRepository
import com.wafuri.idle.application.service.character.CharacterTemplateCatalog
import com.wafuri.idle.application.service.combat.RandomSource
import com.wafuri.idle.application.service.player.PlayerService
import com.wafuri.idle.domain.model.Player
import com.wafuri.idle.domain.model.Team
import com.wafuri.idle.tests.support.clericTemplate
import com.wafuri.idle.tests.support.gameConfig
import com.wafuri.idle.tests.support.warriorTemplate
import io.kotest.assertions.throwables.shouldThrow
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
  private lateinit var playerStateWorkQueue: PlayerStateWorkQueue
  private lateinit var randomSource: RandomSource
  private lateinit var config: GameConfig
  private lateinit var playerSlot: CapturingSlot<Player>
  private lateinit var teamSlot: CapturingSlot<Team>
  private lateinit var service: PlayerService

  init {
    beforeTest {
      playerRepository = mockk()
      teamRepository = mockk()
      characterTemplateCatalog = mockk()
      playerStateWorkQueue = mockk()
      randomSource = mockk()
      config = gameConfig(initialTeamSlots = 3)
      playerSlot = slot()
      teamSlot = slot()
      service = PlayerService(playerRepository, teamRepository, characterTemplateCatalog, playerStateWorkQueue, randomSource, config)
    }

    "provision and get player" {
      every { playerRepository.save(capture(playerSlot)) } answers { playerSlot.captured }
      every { playerRepository.findById(any()) } answers {
        playerSlot.captured.takeIf { it.id == firstArg<UUID>() }
      }
      every { teamRepository.save(capture(teamSlot)) } answers { teamSlot.captured }
      every { playerStateWorkQueue.markDirty(any()) } returns Unit
      val player = service.provision("Alice")

      service.get(player.id) shouldBe player
      player.experience shouldBe 0
      player.level shouldBe 1
      player.gold shouldBe 0
      player.essence shouldBe 0
      player.ownedCharacterKeys shouldBe emptySet()
      player.activeTeamId shouldBe null
      verify(exactly = 1) { playerRepository.save(any()) }
      verify(exactly = 3) { teamRepository.save(any()) }
      verify(exactly = 1) { playerRepository.findById(player.id) }
    }

    "claim starter grants configured starter when player has no owned characters" {
      val playerId = UUID.randomUUID()
      val player = Player(playerId, "Alice")
      every { playerRepository.save(capture(playerSlot)) } answers { playerSlot.captured }
      every { playerRepository.findById(playerId) } returns player
      every { characterTemplateCatalog.require("nimbus") } returns warriorTemplate(key = "nimbus")
      every { playerStateWorkQueue.markDirty(playerId) } returns Unit

      val updated = service.claimStarter(playerId, "nimbus")

      updated.ownedCharacterKeys shouldBe setOf("nimbus")
      verify(exactly = 1) { characterTemplateCatalog.require("nimbus") }
      verify(exactly = 1) { playerRepository.save(any()) }
      verify(exactly = 1) { playerStateWorkQueue.markDirty(playerId) }
    }

    "claim starter rejects players that already own a character" {
      val playerId = UUID.randomUUID()
      every { playerRepository.findById(playerId) } returns Player(playerId, "Alice", ownedCharacterKeys = setOf("nimbus"))

      shouldThrow<com.wafuri.idle.application.exception.ValidationException> {
        service.claimStarter(playerId, "vyron")
      }.message shouldBe "Starter choice is only available for players without owned characters."
    }

    "pull character grants a new character and spends gold" {
      val playerId = UUID.randomUUID()
      val player = Player(playerId, "Alice", gold = 300)
      every { playerRepository.findById(playerId) } returns player
      every { characterTemplateCatalog.all() } returns listOf(clericTemplate(key = "cleric"), warriorTemplate(key = "warrior"))
      every { randomSource.nextInt(2) } returns 1
      every { playerRepository.save(capture(playerSlot)) } answers { playerSlot.captured }
      every { playerStateWorkQueue.markDirty(playerId) } returns Unit

      val result = service.pullCharacter(playerId)

      result.pulledCharacterKey shouldBe "warrior"
      result.grantedCharacterKey shouldBe "warrior"
      result.essenceGranted shouldBe 0
      result.player.gold shouldBe 50
      result.player.ownedCharacterKeys shouldBe setOf("warrior")
      verify(exactly = 1) { playerStateWorkQueue.markDirty(playerId) }
    }

    "pull character converts duplicates into essence" {
      val playerId = UUID.randomUUID()
      val player = Player(playerId, "Alice", ownedCharacterKeys = setOf("warrior"), gold = 300, essence = 5)
      every { playerRepository.findById(playerId) } returns player
      every { characterTemplateCatalog.all() } returns listOf(warriorTemplate(key = "warrior"))
      every { randomSource.nextInt(1) } returns 0
      every { playerRepository.save(capture(playerSlot)) } answers { playerSlot.captured }
      every { playerStateWorkQueue.markDirty(playerId) } returns Unit

      val result = service.pullCharacter(playerId)

      result.pulledCharacterKey shouldBe "warrior"
      result.grantedCharacterKey shouldBe null
      result.essenceGranted shouldBe 15
      result.player.gold shouldBe 50
      result.player.essence shouldBe 20
      result.player.ownedCharacterKeys shouldBe setOf("warrior")
    }

    "pull character rejects players without enough gold" {
      val playerId = UUID.randomUUID()
      every { playerRepository.findById(playerId) } returns Player(playerId, "Alice", gold = 249)

      shouldThrow<com.wafuri.idle.application.exception.ValidationException> {
        service.pullCharacter(playerId)
      }.message shouldBe "Player $playerId does not have enough gold for a character pull."
    }
  }
}
