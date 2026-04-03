package com.wafuri.idle.application.model

import com.wafuri.idle.application.service.player.OfflineProgressionResult
import java.time.Instant
import java.util.UUID

sealed interface PlayerPayload {
  val type: EventType
  val playerId: UUID
}

sealed interface PlayerMessage : PlayerPayload {
  fun publishAt(serverTime: Instant): PlayerMessage = this
}

data class PlayerStateMessage(
  override val type: EventType = EventType.PLAYER_STATE_SYNC,
  override val playerId: UUID,
  val snapshot: PlayerStateSnapshot,
) : PlayerMessage

data class CombatStateMessage(
  override val type: EventType = EventType.COMBAT_STATE_SYNC,
  override val playerId: UUID,
  val snapshot: CombatSnapshot?,
  val serverTime: Instant,
) : PlayerMessage

data class ZoneLevelUpMessage(
  override val playerId: UUID,
  val zoneId: String,
  val level: Int,
  val serverTime: Instant? = null,
  override val type: EventType = EventType.ZONE_LEVEL_UP,
) : PlayerMessage {
  override fun publishAt(serverTime: Instant): PlayerMessage = copy(serverTime = serverTime)
}

data class OfflineRewardSummary(
  val itemName: String,
  val count: Int,
)

data class OfflineProgressionMessage(
  override val type: EventType = EventType.OFFLINE_PROGRESSION,
  override val playerId: UUID,
  val offlineDurationMillis: Long,
  val kills: Int,
  val experienceGained: Int,
  val goldGained: Int,
  val playerLevel: Int,
  val playerLevelsGained: Int,
  val zoneId: String,
  val zoneLevel: Int,
  val zoneLevelsGained: Int,
  val rewards: List<OfflineRewardSummary>,
  val serverTime: Instant? = null,
) : PlayerMessage {
  companion object {
    fun from(result: OfflineProgressionResult): OfflineProgressionMessage =
      OfflineProgressionMessage(
        playerId = result.playerId,
        offlineDurationMillis = result.offlineDuration.toMillis(),
        kills = result.kills,
        experienceGained = result.experienceGained,
        goldGained = result.goldGained,
        playerLevel = result.playerLevel,
        playerLevelsGained = result.playerLevelsGained,
        zoneId = result.zoneId,
        zoneLevel = result.zoneLevel,
        zoneLevelsGained = result.zoneLevelsGained,
        rewards = result.rewards,
      )
  }

  override fun publishAt(serverTime: Instant): PlayerMessage = copy(serverTime = serverTime)
}

data class CommandErrorMessage(
  override val type: EventType = EventType.COMMAND_ERROR,
  override val playerId: UUID,
  val commandType: String,
  val message: String,
  val serverTime: Instant,
) : PlayerMessage
