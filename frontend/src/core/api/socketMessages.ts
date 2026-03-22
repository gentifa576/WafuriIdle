import type { CombatStateSyncMessage, PlayerSocketMessage, PlayerStateSyncMessage } from '../types/api'

export function parsePlayerSocketMessage(raw: string): PlayerSocketMessage | null {
  try {
    const parsed = JSON.parse(raw) as unknown
    if (isPlayerStateSyncMessage(parsed) || isCombatStateSyncMessage(parsed)) {
      return parsed
    }
  } catch {
    return null
  }
  return null
}

function isPlayerStateSyncMessage(message: unknown): message is PlayerStateSyncMessage {
  return (
    typeof message === 'object' &&
    message !== null &&
    'type' in message &&
    message.type === 'PLAYER_STATE_SYNC' &&
    'snapshot' in message
  )
}

function isCombatStateSyncMessage(message: unknown): message is CombatStateSyncMessage {
  return (
    typeof message === 'object' &&
    message !== null &&
    'type' in message &&
    message.type === 'COMBAT_STATE_SYNC' &&
    'snapshot' in message
  )
}
