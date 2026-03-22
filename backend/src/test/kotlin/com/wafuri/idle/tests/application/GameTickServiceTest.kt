package com.wafuri.idle.tests.application

import com.wafuri.idle.application.model.PlayerStateSnapshot
import com.wafuri.idle.application.port.out.ActivePlayerRegistry
import com.wafuri.idle.application.port.out.PlayerStateChangeTracker
import com.wafuri.idle.application.port.out.PlayerStatePublisher
import com.wafuri.idle.application.port.out.PlayerStateWorkQueue
import com.wafuri.idle.application.service.player.PlayerStateSnapshotService
import com.wafuri.idle.application.service.tick.GameTickService
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.util.UUID

class GameTickServiceTest : StringSpec() {
  private lateinit var activePlayerRegistry: ActivePlayerRegistry
  private lateinit var playerStateWorkQueue: PlayerStateWorkQueue
  private lateinit var playerStateSnapshotService: PlayerStateSnapshotService
  private lateinit var playerStateChangeTracker: PlayerStateChangeTracker
  private lateinit var playerStatePublisher: PlayerStatePublisher
  private lateinit var service: GameTickService

  init {
    beforeTest {
      activePlayerRegistry = mockk()
      playerStateWorkQueue = mockk()
      playerStateSnapshotService = mockk()
      playerStateChangeTracker = mockk()
      playerStatePublisher = mockk()
      service =
        GameTickService(
          activePlayerRegistry,
          playerStateWorkQueue,
          playerStateSnapshotService,
          playerStateChangeTracker,
          playerStatePublisher,
        )
    }

    "tick publishes snapshots for active and dirty players" {
      val playerId = UUID.randomUUID()
      val snapshot =
        PlayerStateSnapshot(
          playerId = playerId,
          playerName = "Alice",
          ownedCharacters = emptyList(),
          inventory = emptyList(),
          serverTime = Instant.now(),
        )

      every { activePlayerRegistry.activePlayerIds() } returns setOf(playerId)
      every { playerStateWorkQueue.drainDirtyPlayerIds() } returns setOf(playerId)
      every { playerStateSnapshotService.snapshotFor(playerId) } returns snapshot
      every { playerStateSnapshotService.combatSnapshotFor(playerId) } returns null
      every { playerStateChangeTracker.shouldPublishPlayerState(snapshot) } returns true
      every { playerStateChangeTracker.shouldPublishCombatState(playerId, null) } returns false
      coEvery { playerStatePublisher.publishPlayerState(snapshot) } returns Unit

      runBlocking {
        service.tick()
      }

      verify(exactly = 1) { playerStateSnapshotService.snapshotFor(playerId) }
      verify(exactly = 1) { playerStateChangeTracker.shouldPublishPlayerState(snapshot) }
      coVerify(exactly = 1) { playerStatePublisher.publishPlayerState(snapshot) }
    }

    "tick starts a publish for each player" {
      val playerIds = listOf(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())
      val publishedIds = mutableListOf<UUID>()

      every { activePlayerRegistry.activePlayerIds() } returns playerIds.toSet()
      every { playerStateWorkQueue.drainDirtyPlayerIds() } returns emptySet()
      playerIds.forEach { playerId ->
        every { playerStateSnapshotService.snapshotFor(playerId) } returns
          PlayerStateSnapshot(
            playerId = playerId,
            playerName = playerId.toString(),
            ownedCharacters = emptyList(),
            inventory = emptyList(),
            serverTime = Instant.now(),
          )
        every { playerStateSnapshotService.combatSnapshotFor(playerId) } returns null
        every { playerStateChangeTracker.shouldPublishPlayerState(any()) } returns true
        every { playerStateChangeTracker.shouldPublishCombatState(playerId, null) } returns false
      }
      coEvery { playerStatePublisher.publishPlayerState(any()) } coAnswers {
        publishedIds += firstArg<PlayerStateSnapshot>().playerId
      }

      runBlocking {
        service.tick()
      }

      publishedIds.toSet() shouldBe playerIds.toSet()
    }

    "tick skips publish when player state did not change" {
      val playerId = UUID.randomUUID()
      val snapshot =
        PlayerStateSnapshot(
          playerId = playerId,
          playerName = "Alice",
          ownedCharacters = emptyList(),
          inventory = emptyList(),
          serverTime = Instant.now(),
        )

      every { activePlayerRegistry.activePlayerIds() } returns setOf(playerId)
      every { playerStateWorkQueue.drainDirtyPlayerIds() } returns emptySet()
      every { playerStateSnapshotService.snapshotFor(playerId) } returns snapshot
      every { playerStateSnapshotService.combatSnapshotFor(playerId) } returns null
      every { playerStateChangeTracker.shouldPublishPlayerState(snapshot) } returns false
      every { playerStateChangeTracker.shouldPublishCombatState(playerId, null) } returns false

      runBlocking {
        service.tick()
      }

      verify(exactly = 1) { playerStateChangeTracker.shouldPublishPlayerState(snapshot) }
      coVerify(exactly = 0) { playerStatePublisher.publishPlayerState(any()) }
    }

    "tick publishes combat state separately when only combat changed" {
      val playerId = UUID.randomUUID()
      val snapshot =
        PlayerStateSnapshot(
          playerId = playerId,
          playerName = "Alice",
          ownedCharacters = emptyList(),
          inventory = emptyList(),
          serverTime = Instant.now(),
        )

      every { activePlayerRegistry.activePlayerIds() } returns setOf(playerId)
      every { playerStateWorkQueue.drainDirtyPlayerIds() } returns emptySet()
      every { playerStateSnapshotService.snapshotFor(playerId) } returns snapshot
      every { playerStateSnapshotService.combatSnapshotFor(playerId) } returns null
      every { playerStateChangeTracker.shouldPublishPlayerState(snapshot) } returns false
      every { playerStateChangeTracker.shouldPublishCombatState(playerId, null) } returns true
      coEvery { playerStatePublisher.publishCombatState(playerId, null, any()) } returns Unit

      runBlocking {
        service.tick()
      }

      coVerify(exactly = 0) { playerStatePublisher.publishPlayerState(any()) }
      coVerify(exactly = 1) { playerStatePublisher.publishCombatState(playerId, null, any()) }
    }
  }
}
