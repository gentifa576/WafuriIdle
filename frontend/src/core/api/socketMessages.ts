import type {
  CombatStateSyncMessage,
  OfflineProgressionMessage,
  PlayerSocketMessage,
  PlayerStateSyncMessage,
  ZoneLevelUpMessage,
} from '../types/api'

export function parsePlayerSocketMessage(raw: string): PlayerSocketMessage | null {
  try {
    const parsed = JSON.parse(raw) as unknown
    if (
      isPlayerStateSyncMessage(parsed) ||
      isCombatStateSyncMessage(parsed) ||
      isZoneLevelUpMessage(parsed) ||
      isOfflineProgressionMessage(parsed)
    ) {
      return parsed
    }
  } catch {
    return null
  }
  return null
}

function isPlayerStateSyncMessage(message: unknown): message is PlayerStateSyncMessage {
  return isTypedMessage(message, 'PLAYER_STATE_SYNC') && 'snapshot' in message
}

function isCombatStateSyncMessage(message: unknown): message is CombatStateSyncMessage {
  return isTypedMessage(message, 'COMBAT_STATE_SYNC') && 'snapshot' in message
}

function isZoneLevelUpMessage(message: unknown): message is ZoneLevelUpMessage {
  return isTypedMessage(message, 'ZONE_LEVEL_UP') && 'zoneId' in message && 'level' in message
}

function isOfflineProgressionMessage(message: unknown): message is OfflineProgressionMessage {
  return isTypedMessage(message, 'OFFLINE_PROGRESSION') && 'offlineDurationMillis' in message && 'rewards' in message
}

function isTypedMessage<T extends string>(
  message: unknown,
  type: T,
): message is { type: T } & Record<string, unknown> {
  return typeof message === 'object' && message !== null && 'type' in message && message.type === type
}
