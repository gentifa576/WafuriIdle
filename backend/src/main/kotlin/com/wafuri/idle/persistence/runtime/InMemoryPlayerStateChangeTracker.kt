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
  private val lastPublishedCombat: MutableMap<UUID, CombatSnapshot> = ConcurrentHashMap()

  override fun shouldPublishPlayerState(snapshot: PlayerStateSnapshot): Boolean {
    val newContent = snapshot.content()
    val shouldPublish = AtomicBoolean(false)
    lastPublishedContent.compute(snapshot.playerId) { _, previousContent ->
      when {
        previousContent == null || previousContent != newContent -> {
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
    if (snapshot == null) {
      return lastPublishedCombat.remove(playerId) != null
    }

    val shouldPublish = AtomicBoolean(false)
    lastPublishedCombat.compute(playerId) { _, previousSnapshot ->
      val repeatedDownTick =
        previousSnapshot?.status == CombatStatus.DOWN &&
          snapshot.status == CombatStatus.DOWN
      val cooldownReadyTransition =
        previousSnapshot?.let { hasCooldownReadyTransition(it, snapshot) } ?: false
      val nonCooldownContentChanged =
        previousSnapshot == null || previousSnapshot.withoutCooldowns() != snapshot.withoutCooldowns()
      if (!repeatedDownTick && (nonCooldownContentChanged || cooldownReadyTransition)) {
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

  private fun hasCooldownReadyTransition(
    previousSnapshot: CombatSnapshot,
    snapshot: CombatSnapshot,
  ): Boolean {
    val currentByCharacterKey =
      snapshot.members.associateBy { it.characterKey }
    return previousSnapshot.members.any { previousMember ->
      val currentMember = currentByCharacterKey[previousMember.characterKey] ?: return@any false
      val previousCooldown = previousMember.skillCooldownRemainingMillis ?: 0L
      val currentCooldown = currentMember.skillCooldownRemainingMillis ?: 0L
      previousCooldown > 0L && currentCooldown <= 0L
    }
  }
}

private fun CombatSnapshot.withoutCooldowns(): CombatSnapshot =
  copy(
    members =
      members.map { member ->
        member.copy(skillCooldownRemainingMillis = null)
      },
  )
