package com.wafuri.idle.tests.application

import com.wafuri.idle.application.config.GameConfig
import com.wafuri.idle.application.model.ZoneLevelUpMessage
import com.wafuri.idle.application.port.out.PlayerMessageQueue
import com.wafuri.idle.application.port.out.PlayerStateWorkQueue
import com.wafuri.idle.application.port.out.PlayerZoneProgressRepository
import com.wafuri.idle.application.port.out.Repository
import com.wafuri.idle.application.service.combat.CombatStatService
import com.wafuri.idle.application.service.player.ProgressionService
import com.wafuri.idle.application.service.scaling.ScalingRule
import com.wafuri.idle.domain.model.Player
import com.wafuri.idle.domain.model.PlayerZoneProgress
import com.wafuri.idle.tests.support.expectedPlayer
import com.wafuri.idle.tests.support.expectedZoneProgress
import com.wafuri.idle.tests.support.gameConfig
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import java.util.UUID

class ProgressionServiceTest : StringSpec() {
  private lateinit var playerRepository: Repository<Player, UUID>
  private lateinit var playerZoneProgressRepository: PlayerZoneProgressRepository
  private lateinit var playerEventQueue: PlayerMessageQueue
  private lateinit var playerStateWorkQueue: PlayerStateWorkQueue
  private lateinit var combatStatService: CombatStatService
  private lateinit var config: GameConfig
  private lateinit var service: ProgressionService

  init {
    beforeTest {
      playerRepository = mockk()
      playerZoneProgressRepository = mockk()
      playerEventQueue = mockk()
      playerStateWorkQueue = mockk()
      combatStatService = mockk()
      config = gameConfig(killExperience = 25, experiencePerLevel = 100, zoneKillsPerLevel = 3)
      service =
        ProgressionService(
          playerRepository,
          playerZoneProgressRepository,
          playerEventQueue,
          playerStateWorkQueue,
          combatStatService,
          ScalingRule(config),
          config,
        )
    }

    "record kill grants player experience and creates zone progress" {
      val playerId = UUID.randomUUID()
      val zoneId = "starter-plains"
      val player = expectedPlayer(playerId, "Alice")
      var savedPlayer: Player? = null
      var savedProgress: PlayerZoneProgress? = null

      every { playerRepository.require(playerId) } returns player
      every { playerRepository.save(any()) } answers { firstArg<Player>().also { savedPlayer = it } }
      every { playerZoneProgressRepository.findByPlayerIdAndZoneId(playerId, zoneId) } returns null
      every { playerZoneProgressRepository.save(any()) } answers {
        firstArg<PlayerZoneProgress>().also { savedProgress = it }
      }
      every { playerEventQueue.enqueue(any()) } just runs
      every { playerStateWorkQueue.markDirty(playerId) } just runs
      every { combatStatService.invalidatePlayer(any()) } just runs

      service.recordKill(playerId, zoneId)

      savedPlayer shouldBe expectedPlayer(playerId, "Alice", experience = 25, level = 1, gold = 25)
      savedProgress shouldBe expectedZoneProgress(playerId, zoneId, 16, 5)
      verify(exactly = 1) {
        playerEventQueue.enqueue(ZoneLevelUpMessage(playerId = playerId, zoneId = zoneId, level = 5))
      }
      verify(exactly = 0) { combatStatService.invalidatePlayer(any()) }
      verify(exactly = 1) { playerStateWorkQueue.markDirty(playerId) }
    }

    "record kill scales experience and gold rewards by enemy zone level" {
      val playerId = UUID.randomUUID()
      val zoneId = "starter-plains"
      val player = expectedPlayer(playerId, "Alice")
      var savedPlayer: Player? = null
      var savedProgress: PlayerZoneProgress? = null

      every { playerRepository.require(playerId) } returns player
      every { playerRepository.save(any()) } answers { firstArg<Player>().also { savedPlayer = it } }
      every { playerZoneProgressRepository.findByPlayerIdAndZoneId(playerId, zoneId) } returns null
      every { playerZoneProgressRepository.save(any()) } answers {
        firstArg<PlayerZoneProgress>().also { savedProgress = it }
      }
      every { playerEventQueue.enqueue(any()) } just runs
      every { playerStateWorkQueue.markDirty(playerId) } just runs
      every { combatStatService.invalidatePlayer(any()) } just runs

      service.recordKill(playerId, zoneId, 50)

      savedPlayer shouldBe expectedPlayer(playerId, "Alice", experience = 38, level = 1, gold = 38)
      savedProgress shouldBe expectedZoneProgress(playerId, zoneId, 24, 6)
    }

    "record kill levels player and zone based on configured thresholds" {
      val playerId = UUID.randomUUID()
      val zoneId = "starter-plains"
      val currentPlayer = expectedPlayer(playerId, "Alice", experience = 90, level = 1)
      val currentProgress =
        PlayerZoneProgress(playerId, zoneId, 2, 1)
      var savedPlayer: Player? = null
      var savedProgress: PlayerZoneProgress? = null

      every { playerRepository.require(playerId) } returns currentPlayer
      every { playerRepository.save(any()) } answers { firstArg<Player>().also { savedPlayer = it } }
      every { playerZoneProgressRepository.findByPlayerIdAndZoneId(playerId, zoneId) } returns currentProgress
      every { playerZoneProgressRepository.save(any()) } answers {
        firstArg<PlayerZoneProgress>().also { savedProgress = it }
      }
      every { playerEventQueue.enqueue(any()) } just runs
      every { playerStateWorkQueue.markDirty(playerId) } just runs
      every { combatStatService.invalidatePlayer(playerId) } just runs

      service.recordKill(playerId, zoneId)

      savedPlayer shouldBe expectedPlayer(playerId, "Alice", experience = 115, level = 2, gold = 25)
      savedProgress shouldBe expectedZoneProgress(playerId, zoneId, 18, 5)
      verify(exactly = 1) {
        playerEventQueue.enqueue(ZoneLevelUpMessage(playerId = playerId, zoneId = zoneId, level = 5))
      }
      verify(exactly = 1) { combatStatService.invalidatePlayer(playerId) }
    }

    "record kill can grant multiple zone progress points from one kill" {
      val playerId = UUID.randomUUID()
      val zoneId = "starter-plains"
      val currentPlayer = expectedPlayer(playerId, "Alice")
      val currentProgress = PlayerZoneProgress(playerId, zoneId, 2, 1)
      var savedProgress: PlayerZoneProgress? = null

      config = gameConfig(killExperience = 25, experiencePerLevel = 100, zoneKillsPerLevel = 3, zoneProgressMultiplier = 2.5f)
      service =
        ProgressionService(
          playerRepository,
          playerZoneProgressRepository,
          playerEventQueue,
          playerStateWorkQueue,
          combatStatService,
          ScalingRule(config),
          config,
        )

      every { playerRepository.require(playerId) } returns currentPlayer
      every { playerRepository.save(any()) } answers { firstArg<Player>() }
      every { playerZoneProgressRepository.findByPlayerIdAndZoneId(playerId, zoneId) } returns currentProgress
      every { playerZoneProgressRepository.save(any()) } answers {
        firstArg<PlayerZoneProgress>().also { savedProgress = it }
      }
      every { playerEventQueue.enqueue(any()) } just runs
      every { playerStateWorkQueue.markDirty(playerId) } just runs
      every { combatStatService.invalidatePlayer(any()) } just runs

      service.recordKill(playerId, zoneId)

      savedProgress shouldBe expectedZoneProgress(playerId, zoneId, 5, 2)
      verify(exactly = 1) {
        playerEventQueue.enqueue(ZoneLevelUpMessage(playerId = playerId, zoneId = zoneId, level = 2))
      }
    }
  }
}
