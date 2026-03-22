package com.wafuri.idle.tests.application

import com.wafuri.idle.application.model.CombatStateMessage
import com.wafuri.idle.application.model.EventType
import com.wafuri.idle.application.model.OfflineProgressionMessage
import com.wafuri.idle.application.model.OfflineRewardSummary
import com.wafuri.idle.application.model.PlayerMessage
import com.wafuri.idle.application.model.PlayerStateMessage
import com.wafuri.idle.application.model.PlayerStateSnapshot
import com.wafuri.idle.application.model.ZoneLevelUpMessage
import com.wafuri.idle.application.port.out.ActivePlayerRegistry
import com.wafuri.idle.application.port.out.PlayerMessagePublisher
import com.wafuri.idle.application.port.out.PlayerMessageQueue
import com.wafuri.idle.application.port.out.PlayerStateChangeTracker
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
  private lateinit var playerEventQueue: PlayerMessageQueue
  private lateinit var playerStateWorkQueue: PlayerStateWorkQueue
  private lateinit var playerStateSnapshotService: PlayerStateSnapshotService
  private lateinit var playerStateChangeTracker: PlayerStateChangeTracker
  private lateinit var playerMessagePublisher: PlayerMessagePublisher
  private lateinit var service: GameTickService

  init {
    beforeTest {
      activePlayerRegistry = mockk()
      playerEventQueue = mockk()
      playerStateWorkQueue = mockk()
      playerStateSnapshotService = mockk()
      playerStateChangeTracker = mockk()
      playerMessagePublisher = mockk()
      service =
        GameTickService(
          activePlayerRegistry,
          playerEventQueue,
          playerStateWorkQueue,
          playerStateSnapshotService,
          playerStateChangeTracker,
          playerMessagePublisher,
        )
    }

    "tick publishes snapshots for active and dirty players" {
      val playerId = UUID.randomUUID()
      val snapshot =
        PlayerStateSnapshot(
          playerId = playerId,
          playerName = "Alice",
          playerExperience = 0,
          playerLevel = 1,
          ownedCharacters = emptyList(),
          zoneProgress = emptyList(),
          inventory = emptyList(),
          serverTime = Instant.now(),
        )

      every { activePlayerRegistry.activePlayerIds() } returns setOf(playerId)
      every { playerEventQueue.drainGroupedByPlayerId() } returns emptyMap()
      every { playerStateWorkQueue.drainDirtyPlayerIds() } returns setOf(playerId)
      every { playerStateSnapshotService.snapshotFor(playerId) } returns snapshot
      every { playerStateSnapshotService.combatSnapshotFor(playerId) } returns null
      every { playerStateChangeTracker.shouldPublishPlayerState(snapshot) } returns true
      every { playerStateChangeTracker.shouldPublishCombatState(playerId, null) } returns false
      coEvery { playerMessagePublisher.publish(any()) } returns Unit

      runBlocking {
        service.tick()
      }

      verify(exactly = 1) { playerStateSnapshotService.snapshotFor(playerId) }
      verify(exactly = 1) { playerStateChangeTracker.shouldPublishPlayerState(snapshot) }
      coVerify(exactly = 1) {
        playerMessagePublisher.publish(
          PlayerStateMessage(
            playerId = playerId,
            snapshot = snapshot,
          ),
        )
      }
    }

    "tick starts a publish for each player" {
      val playerIds = listOf(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())
      val publishedIds = mutableListOf<UUID>()

      every { activePlayerRegistry.activePlayerIds() } returns playerIds.toSet()
      every { playerEventQueue.drainGroupedByPlayerId() } returns emptyMap()
      every { playerStateWorkQueue.drainDirtyPlayerIds() } returns emptySet()
      playerIds.forEach { playerId ->
        every { playerStateSnapshotService.snapshotFor(playerId) } returns
          PlayerStateSnapshot(
            playerId = playerId,
            playerName = playerId.toString(),
            playerExperience = 0,
            playerLevel = 1,
            ownedCharacters = emptyList(),
            zoneProgress = emptyList(),
            inventory = emptyList(),
            serverTime = Instant.now(),
          )
        every { playerStateSnapshotService.combatSnapshotFor(playerId) } returns null
        every { playerStateChangeTracker.shouldPublishPlayerState(any()) } returns true
        every { playerStateChangeTracker.shouldPublishCombatState(playerId, null) } returns false
      }
      coEvery { playerMessagePublisher.publish(any()) } coAnswers {
        publishedIds += firstArg<PlayerMessage>().playerId
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
          playerExperience = 0,
          playerLevel = 1,
          ownedCharacters = emptyList(),
          zoneProgress = emptyList(),
          inventory = emptyList(),
          serverTime = Instant.now(),
        )

      every { activePlayerRegistry.activePlayerIds() } returns setOf(playerId)
      every { playerEventQueue.drainGroupedByPlayerId() } returns emptyMap()
      every { playerStateWorkQueue.drainDirtyPlayerIds() } returns emptySet()
      every { playerStateSnapshotService.snapshotFor(playerId) } returns snapshot
      every { playerStateSnapshotService.combatSnapshotFor(playerId) } returns null
      every { playerStateChangeTracker.shouldPublishPlayerState(snapshot) } returns false
      every { playerStateChangeTracker.shouldPublishCombatState(playerId, null) } returns false

      runBlocking {
        service.tick()
      }

      verify(exactly = 1) { playerStateChangeTracker.shouldPublishPlayerState(snapshot) }
      coVerify(exactly = 0) { playerMessagePublisher.publish(any()) }
    }

    "tick publishes combat state separately when only combat changed" {
      val playerId = UUID.randomUUID()
      val snapshot =
        PlayerStateSnapshot(
          playerId = playerId,
          playerName = "Alice",
          playerExperience = 0,
          playerLevel = 1,
          ownedCharacters = emptyList(),
          zoneProgress = emptyList(),
          inventory = emptyList(),
          serverTime = Instant.now(),
        )

      every { activePlayerRegistry.activePlayerIds() } returns setOf(playerId)
      every { playerEventQueue.drainGroupedByPlayerId() } returns emptyMap()
      every { playerStateWorkQueue.drainDirtyPlayerIds() } returns emptySet()
      every { playerStateSnapshotService.snapshotFor(playerId) } returns snapshot
      every { playerStateSnapshotService.combatSnapshotFor(playerId) } returns null
      every { playerStateChangeTracker.shouldPublishPlayerState(snapshot) } returns false
      every { playerStateChangeTracker.shouldPublishCombatState(playerId, null) } returns true
      coEvery { playerMessagePublisher.publish(any()) } returns Unit

      runBlocking {
        service.tick()
      }

      coVerify(exactly = 1) {
        playerMessagePublisher.publish(
          match<PlayerMessage> {
            it is CombatStateMessage &&
              it.type == EventType.COMBAT_STATE_SYNC &&
              it.playerId == playerId &&
              it.snapshot == null
          },
        )
      }
    }

    "tick publishes queued player events even when state did not change" {
      val playerId = UUID.randomUUID()
      val snapshot =
        PlayerStateSnapshot(
          playerId = playerId,
          playerName = "Alice",
          playerExperience = 0,
          playerLevel = 1,
          ownedCharacters = emptyList(),
          zoneProgress = emptyList(),
          inventory = emptyList(),
          serverTime = Instant.now(),
        )
      val events = listOf(ZoneLevelUpMessage(playerId = playerId, zoneId = "starter-plains", level = 2))

      every { activePlayerRegistry.activePlayerIds() } returns emptySet()
      every { playerEventQueue.drainGroupedByPlayerId() } returns mapOf(playerId to events)
      every { playerStateWorkQueue.drainDirtyPlayerIds() } returns emptySet()
      every { playerStateSnapshotService.snapshotFor(playerId) } returns snapshot
      every { playerStateSnapshotService.combatSnapshotFor(playerId) } returns null
      every { playerStateChangeTracker.shouldPublishPlayerState(snapshot) } returns false
      every { playerStateChangeTracker.shouldPublishCombatState(playerId, null) } returns false
      coEvery { playerMessagePublisher.publish(any()) } returns Unit

      runBlocking {
        service.tick()
      }

      coVerify(exactly = 1) {
        playerMessagePublisher.publish(
          match<PlayerMessage> {
            it.type == EventType.ZONE_LEVEL_UP && it.playerId == playerId
          },
        )
      }
    }

    "tick publishes offline progression summaries from the event queue" {
      val playerId = UUID.randomUUID()
      val snapshot =
        PlayerStateSnapshot(
          playerId = playerId,
          playerName = "Alice",
          playerExperience = 0,
          playerLevel = 1,
          ownedCharacters = emptyList(),
          zoneProgress = emptyList(),
          inventory = emptyList(),
          serverTime = Instant.now(),
        )
      val events =
        listOf(
          OfflineProgressionMessage(
            playerId = playerId,
            offlineDurationMillis = 360_000,
            kills = 32,
            experienceGained = 320,
            playerLevel = 4,
            playerLevelsGained = 3,
            zoneId = "starter-plains",
            zoneLevel = 4,
            zoneLevelsGained = 3,
            rewards = listOf(OfflineRewardSummary(itemName = "Sword", count = 32)),
          ),
        )

      every { activePlayerRegistry.activePlayerIds() } returns emptySet()
      every { playerEventQueue.drainGroupedByPlayerId() } returns mapOf(playerId to events)
      every { playerStateWorkQueue.drainDirtyPlayerIds() } returns emptySet()
      every { playerStateSnapshotService.snapshotFor(playerId) } returns snapshot
      every { playerStateSnapshotService.combatSnapshotFor(playerId) } returns null
      every { playerStateChangeTracker.shouldPublishPlayerState(snapshot) } returns false
      every { playerStateChangeTracker.shouldPublishCombatState(playerId, null) } returns false
      coEvery { playerMessagePublisher.publish(any()) } returns Unit

      runBlocking {
        service.tick()
      }

      coVerify(exactly = 1) {
        playerMessagePublisher.publish(
          match<PlayerMessage> {
            it.type == EventType.OFFLINE_PROGRESSION && it.playerId == playerId
          },
        )
      }
    }

    "tick suppresses zone level-up events already covered by offline progression" {
      val playerId = UUID.randomUUID()
      val snapshot =
        PlayerStateSnapshot(
          playerId = playerId,
          playerName = "Alice",
          playerExperience = 0,
          playerLevel = 1,
          ownedCharacters = emptyList(),
          zoneProgress = emptyList(),
          inventory = emptyList(),
          serverTime = Instant.now(),
        )
      val events =
        listOf(
          ZoneLevelUpMessage(playerId = playerId, zoneId = "starter-plains", level = 2),
          ZoneLevelUpMessage(playerId = playerId, zoneId = "starter-plains", level = 3),
          OfflineProgressionMessage(
            playerId = playerId,
            offlineDurationMillis = 360_000,
            kills = 20,
            experienceGained = 200,
            playerLevel = 3,
            playerLevelsGained = 2,
            zoneId = "starter-plains",
            zoneLevel = 3,
            zoneLevelsGained = 2,
            rewards = emptyList(),
          ),
          ZoneLevelUpMessage(playerId = playerId, zoneId = "other-zone", level = 2),
        )

      every { activePlayerRegistry.activePlayerIds() } returns emptySet()
      every { playerEventQueue.drainGroupedByPlayerId() } returns mapOf(playerId to events)
      every { playerStateWorkQueue.drainDirtyPlayerIds() } returns emptySet()
      every { playerStateSnapshotService.snapshotFor(playerId) } returns snapshot
      every { playerStateSnapshotService.combatSnapshotFor(playerId) } returns null
      every { playerStateChangeTracker.shouldPublishPlayerState(snapshot) } returns false
      every { playerStateChangeTracker.shouldPublishCombatState(playerId, null) } returns false
      coEvery { playerMessagePublisher.publish(any()) } returns Unit

      runBlocking {
        service.tick()
      }

      coVerify(exactly = 1) {
        playerMessagePublisher.publish(
          match<PlayerMessage> {
            it.type == EventType.OFFLINE_PROGRESSION && it.playerId == playerId
          },
        )
      }
      coVerify(exactly = 1) {
        playerMessagePublisher.publish(
          match<PlayerMessage> {
            it.type == EventType.ZONE_LEVEL_UP && it.playerId == playerId && it is ZoneLevelUpMessage && it.zoneId == "other-zone"
          },
        )
      }
      coVerify(exactly = 0) {
        playerMessagePublisher.publish(
          match<PlayerMessage> {
            it.type == EventType.ZONE_LEVEL_UP && it.playerId == playerId && it is ZoneLevelUpMessage && it.zoneId == "starter-plains"
          },
        )
      }
    }
  }
}
