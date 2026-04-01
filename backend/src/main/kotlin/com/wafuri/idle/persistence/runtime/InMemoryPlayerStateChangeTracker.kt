package com.wafuri.idle.persistence.runtime

import com.wafuri.idle.application.model.CombatSnapshot
import com.wafuri.idle.application.model.PlayerStateContent
import com.wafuri.idle.application.model.PlayerStateSnapshot
import com.wafuri.idle.application.port.out.PlayerStateChangeTracker
import com.wafuri.idle.domain.model.CombatStatus
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

@ApplicationScoped
class InMemoryPlayerStateChangeTracker : PlayerStateChangeTracker {
  private val lastPublishedContent: MutableMap<UUID, PlayerStateContent> = ConcurrentHashMap()
  private val lastPublishedCombat: MutableMap<UUID, CombatSnapshot?> = ConcurrentHashMap()

  override fun shouldPublishPlayerState(snapshot: PlayerStateSnapshot): Boolean {
    val newContent = snapshot.content()
    val shouldPublish = AtomicBoolean(false)
    lastPublishedContent.compute(snapshot.playerId) { _, previousContent ->
      when {
        previousContent == null -> {
          shouldPublish.set(true)
          newContent
        }
        previousContent != newContent -> {
          shouldPublish.set(true)
          newContent
        }
        else -> previousContent
      }
    }
    return shouldPublish.get()
  }

  override fun shouldPublishCombatState(
    playerId: UUID,
    snapshot: CombatSnapshot?,
  ): Boolean {
    val shouldPublish = AtomicBoolean(false)
    lastPublishedCombat.compute(playerId) { _, previousSnapshot ->
      val repeatedDownTick =
        previousSnapshot?.status == CombatStatus.DOWN &&
          snapshot?.status == CombatStatus.DOWN
      if (!repeatedDownTick && previousSnapshot != snapshot) {
        shouldPublish.set(true)
      }
      snapshot
    }
    return shouldPublish.get()
  }

  override fun invalidate(playerId: UUID) {
    lastPublishedContent.remove(playerId)
    lastPublishedCombat.remove(playerId)
  }
}
