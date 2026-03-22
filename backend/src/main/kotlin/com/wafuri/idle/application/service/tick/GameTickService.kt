package com.wafuri.idle.application.service.tick

import com.wafuri.idle.application.exception.ResourceNotFoundException
import com.wafuri.idle.application.model.CombatSnapshot
import com.wafuri.idle.application.model.PlayerStateSnapshot
import com.wafuri.idle.application.port.out.ActivePlayerRegistry
import com.wafuri.idle.application.port.out.PlayerStateChangeTracker
import com.wafuri.idle.application.port.out.PlayerStatePublisher
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
  private val playerStateWorkQueue: PlayerStateWorkQueue,
  private val playerStateSnapshotService: PlayerStateSnapshotService,
  private val playerStateChangeTracker: PlayerStateChangeTracker,
  private val playerStatePublisher: PlayerStatePublisher,
) {
  suspend fun tick() =
    coroutineScope {
      collectSnapshotsToPublish()
        .flatMap { publication ->
          buildList {
            publication.playerSnapshot?.let { snapshot ->
              add(
                async {
                  playerStatePublisher.publishPlayerState(snapshot)
                },
              )
            }
            if (publication.publishCombatState) {
              add(
                async {
                  playerStatePublisher.publishCombatState(
                    publication.playerId,
                    publication.combatSnapshot,
                    publication.serverTime,
                  )
                },
              )
            }
          }
        }.awaitAll()
    }

  @Transactional
  fun collectSnapshotsToPublish(): List<PendingPlayerStatePublication> {
    val activePlayerIds = activePlayerRegistry.activePlayerIds()
    val playerIds = activePlayerIds + playerStateWorkQueue.drainDirtyPlayerIds()
    return playerIds
      .mapNotNull(::publicationOrNull)
  }

  private fun publicationOrNull(playerId: UUID): PendingPlayerStatePublication? =
    try {
      val playerSnapshot = playerStateSnapshotService.snapshotFor(playerId)
      val combatSnapshot = playerStateSnapshotService.combatSnapshotFor(playerId)
      val publishPlayerState = playerStateChangeTracker.shouldPublishPlayerState(playerSnapshot)
      val publishCombatState = playerStateChangeTracker.shouldPublishCombatState(playerId, combatSnapshot)
      if (!publishPlayerState && !publishCombatState) {
        return null
      }
      PendingPlayerStatePublication(
        playerId = playerId,
        playerSnapshot = playerSnapshot.takeIf { publishPlayerState },
        combatSnapshot = combatSnapshot,
        publishCombatState = publishCombatState,
        serverTime = Instant.now(),
      )
    } catch (_: ResourceNotFoundException) {
      null
    }
}

data class PendingPlayerStatePublication(
  val playerId: UUID,
  val playerSnapshot: PlayerStateSnapshot?,
  val combatSnapshot: CombatSnapshot?,
  val publishCombatState: Boolean,
  val serverTime: Instant,
)
