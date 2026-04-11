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
  override val playerId: UUID,
  val snapshot: PlayerStateSnapshot,
  override val type: EventType = EventType.PLAYER_STATE_SYNC,
) : PlayerMessage

data class CombatStateMessage(
  override val playerId: UUID,
  val snapshot: CombatSnapshot?,
  val serverTime: Instant,
  override val type: EventType = EventType.COMBAT_STATE_SYNC,
) : PlayerMessage

data class SkillEventsMessage(
  override val playerId: UUID,
  val events: List<SkillEffectEvent>,
  val serverTime: Instant? = null,
  override val type: EventType = EventType.SKILL_EVENTS,
) : PlayerMessage {
  override fun publishAt(serverTime: Instant): PlayerMessage = copy(serverTime = serverTime)
}

data class SkillEffectEvent(
  val eventId: UUID,
  val characterKey: String,
  val skillKey: String,
  val effectType: SkillEffectType,
  val targetType: SkillTargetType,
  val targetKey: String? = null,
  val value: Float? = null,
  val statusKey: String? = null,
  val durationMillis: Long? = null,
)

enum class SkillEffectType {
  DAMAGE,
  HEAL,
  BUFF_APPLIED,
  DEBUFF_APPLIED,
  SHIELD,
}

enum class SkillTargetType {
  ENEMY,
  ALLY_TEAM,
  ALLY_MEMBER,
  SELF,
}

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
  override val playerId: UUID,
  val commandType: String,
  val message: String,
  val serverTime: Instant,
  override val type: EventType = EventType.COMMAND_ERROR,
) : PlayerMessage
