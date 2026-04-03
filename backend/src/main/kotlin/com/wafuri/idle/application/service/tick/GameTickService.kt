package com.wafuri.idle.application.service.tick

import com.wafuri.idle.application.exception.ResourceNotFoundException
import com.wafuri.idle.application.model.CombatSnapshot
import com.wafuri.idle.application.model.CombatStateMessage
import com.wafuri.idle.application.model.OfflineProgressionMessage
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
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.time.Instant
import java.util.UUID

@ApplicationScoped
class GameTickService(
  private val activePlayerRegistry: ActivePlayerRegistry,
  private val playerEventQueue: PlayerMessageQueue,
  private val playerStateWorkQueue: PlayerStateWorkQueue,
  private val playerStateSnapshotService: PlayerStateSnapshotService,
  private val playerStateChangeTracker: PlayerStateChangeTracker,
  private val playerMessagePublisher: PlayerMessagePublisher,
) {
  suspend fun tick() =
    coroutineScope {
      collectSnapshotsToPublish()
        .flatMap {
          it.messages().map { message ->
            async { playerMessagePublisher.publish(message) }
          }
        }.awaitAll()
    }

  @Transactional
  fun collectSnapshotsToPublish(): List<PendingPlayerStatePublication> {
    val activePlayerIds = activePlayerRegistry.activePlayerIds()
    val queuedEventsByPlayerId = playerEventQueue.drainGroupedByPlayerId()
    val playerIds = activePlayerIds + playerStateWorkQueue.drainDirtyPlayerIds() + queuedEventsByPlayerId.keys
    return playerIds
      .mapNotNull { publicationOrNull(it, queuedEventsByPlayerId[it].orEmpty()) }
  }

  private fun publicationOrNull(
    playerId: UUID,
    events: List<PlayerMessage>,
  ): PendingPlayerStatePublication? =
    try {
      val playerSnapshot = playerStateSnapshotService.snapshotFor(playerId)
      val combatSnapshot = playerStateSnapshotService.combatSnapshotFor(playerId)
      val publishPlayerState = playerStateChangeTracker.shouldPublishPlayerState(playerSnapshot)
      val publishCombatState = playerStateChangeTracker.shouldPublishCombatState(playerId, combatSnapshot)
      if (!publishPlayerState && !publishCombatState && events.isEmpty()) {
        return null
      }
      val compactedEvents = compactEvents(events)
      PendingPlayerStatePublication(
        playerId,
        compactedEvents,
        playerSnapshot.takeIf { publishPlayerState },
        combatSnapshot,
        publishCombatState,
        Instant.now(),
      )
    } catch (_: ResourceNotFoundException) {
      null
    }

  private fun compactEvents(events: List<PlayerMessage>): List<PlayerMessage> {
    val offlineZones =
      events
        .filterIsInstance<OfflineProgressionMessage>()
        .map { it.zoneId }
        .toSet()
    if (offlineZones.isEmpty()) {
      return events
    }
    return events.filterNot { it is ZoneLevelUpMessage && it.zoneId in offlineZones }
  }
}

data class PendingPlayerStatePublication(
  val playerId: UUID,
  val events: List<PlayerMessage>,
  val playerSnapshot: PlayerStateSnapshot?,
  val combatSnapshot: CombatSnapshot?,
  val publishCombatState: Boolean,
  val serverTime: Instant,
) {
  fun messages(): List<PlayerMessage> =
    buildList {
      addAll(events.map { it.publishAt(serverTime) })
      playerSnapshot?.let { add(PlayerStateMessage(it.playerId, it)) }
      if (publishCombatState) {
        add(CombatStateMessage(playerId, combatSnapshot, serverTime))
      }
    }
}
