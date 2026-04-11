import type {
  CombatMember,
  CombatSnapshot,
  CombatStateSyncMessage,
  CommandErrorMessage,
  InventoryItemSnapshot,
  OfflineProgressionMessage,
  OfflineRewardSummary,
  OwnedCharacterSnapshot,
  PlayerSocketMessage,
  PlayerStateSnapshot,
  PlayerStateSyncMessage,
  SkillEventsMessage,
  SkillEffectEvent,
  Stat,
  ZoneLevelUpMessage,
  ZoneProgressSnapshot,
} from '../types/api'

export interface SocketMessageParseError {
  code: 'INVALID_JSON' | 'INVALID_MESSAGE_SHAPE' | 'UNSUPPORTED_MESSAGE'
  message: string
}

export type SocketMessageParseResult =
  | { ok: true; message: PlayerSocketMessage }
  | { ok: false; error: SocketMessageParseError }

export function parsePlayerSocketMessage(raw: string): SocketMessageParseResult {
  let parsed: unknown

  try {
    parsed = JSON.parse(raw) as unknown
  } catch {
    return invalidMessage('INVALID_JSON', 'Received invalid JSON from the player socket.')
  }

  if (!isRecord(parsed)) {
    return invalidMessage('INVALID_MESSAGE_SHAPE', 'Received a non-object socket payload.')
  }

  const type = parsed.type
  if (typeof type !== 'string') {
    return invalidMessage('INVALID_MESSAGE_SHAPE', 'Socket payload is missing a string message type.')
  }

  switch (type) {
    case 'PLAYER_STATE_SYNC':
      return parsePlayerStateSyncMessage(parsed)
    case 'COMBAT_STATE_SYNC':
      return parseCombatStateSyncMessage(parsed)
    case 'SKILL_EVENTS':
      return parseSkillEventsMessage(parsed)
    case 'ZONE_LEVEL_UP':
      return parseZoneLevelUpMessage(parsed)
    case 'OFFLINE_PROGRESSION':
      return parseOfflineProgressionMessage(parsed)
    case 'COMMAND_ERROR':
      return parseCommandErrorMessage(parsed)
    default:
      return invalidMessage('UNSUPPORTED_MESSAGE', `Unsupported socket message type "${type}".`)
  }
}

function parsePlayerStateSyncMessage(message: Record<string, unknown>): SocketMessageParseResult {
  if (!isString(message.playerId) || !isPlayerStateSnapshot(message.snapshot)) {
    return invalidMessage('INVALID_MESSAGE_SHAPE', 'PLAYER_STATE_SYNC payload is missing required player snapshot fields.')
  }
  return {
    ok: true,
    message: {
      type: 'PLAYER_STATE_SYNC',
      playerId: message.playerId,
      snapshot: message.snapshot,
    } satisfies PlayerStateSyncMessage,
  }
}

function parseCombatStateSyncMessage(message: Record<string, unknown>): SocketMessageParseResult {
  if (!isString(message.playerId) || !isString(message.serverTime) || !isNullableCombatSnapshot(message.snapshot)) {
    return invalidMessage('INVALID_MESSAGE_SHAPE', 'COMBAT_STATE_SYNC payload is missing required combat snapshot fields.')
  }
  return {
    ok: true,
    message: {
      type: 'COMBAT_STATE_SYNC',
      playerId: message.playerId,
      snapshot: message.snapshot,
      serverTime: message.serverTime,
    } satisfies CombatStateSyncMessage,
  }
}

function parseSkillEventsMessage(message: Record<string, unknown>): SocketMessageParseResult {
  if (
    !isString(message.playerId) ||
    !isSkillEffectEventArray(message.events) ||
    !isString(message.serverTime)
  ) {
    return invalidMessage('INVALID_MESSAGE_SHAPE', 'SKILL_EVENTS payload is missing required fields.')
  }
  return {
    ok: true,
    message: {
      type: 'SKILL_EVENTS',
      playerId: message.playerId,
      events: message.events,
      serverTime: message.serverTime,
    } satisfies SkillEventsMessage,
  }
}

function isSkillEffectEventArray(value: unknown): value is SkillEffectEvent[] {
  return Array.isArray(value) && value.every(isSkillEffectEvent)
}

function isSkillEffectEvent(value: unknown): value is SkillEffectEvent {
  return (
    isRecord(value) &&
    isString(value.eventId) &&
    isString(value.characterKey) &&
    isString(value.skillKey) &&
    isSkillEffectType(value.effectType) &&
    isSkillTargetType(value.targetType) &&
    isNullableString(value.targetKey) &&
    isNullableNumber(value.value) &&
    isNullableString(value.statusKey) &&
    isNullableNumber(value.durationMillis)
  )
}

function isSkillEffectType(value: unknown): boolean {
  return value === 'DAMAGE' || value === 'HEAL' || value === 'BUFF_APPLIED' || value === 'DEBUFF_APPLIED' || value === 'SHIELD'
}

function isSkillTargetType(value: unknown): boolean {
  return value === 'ENEMY' || value === 'ALLY_TEAM' || value === 'ALLY_MEMBER' || value === 'SELF'
}

function parseZoneLevelUpMessage(message: Record<string, unknown>): SocketMessageParseResult {
  if (!isString(message.playerId) || !isString(message.zoneId) || !isNumber(message.level) || !isNullableString(message.serverTime)) {
    return invalidMessage('INVALID_MESSAGE_SHAPE', 'ZONE_LEVEL_UP payload is missing required fields.')
  }
  return {
    ok: true,
    message: {
      type: 'ZONE_LEVEL_UP',
      playerId: message.playerId,
      zoneId: message.zoneId,
      level: message.level,
      serverTime: message.serverTime,
    } satisfies ZoneLevelUpMessage,
  }
}

function parseOfflineProgressionMessage(message: Record<string, unknown>): SocketMessageParseResult {
  if (
    !isString(message.playerId) ||
    !isNumber(message.offlineDurationMillis) ||
    !isNumber(message.kills) ||
    !isNumber(message.experienceGained) ||
    !isNumber(message.goldGained) ||
    !isNumber(message.playerLevel) ||
    !isNumber(message.playerLevelsGained) ||
    !isString(message.zoneId) ||
    !isNumber(message.zoneLevel) ||
    !isNumber(message.zoneLevelsGained) ||
    !isOfflineRewardSummaryArray(message.rewards) ||
    !isNullableString(message.serverTime)
  ) {
    return invalidMessage('INVALID_MESSAGE_SHAPE', 'OFFLINE_PROGRESSION payload is missing required fields.')
  }
  return {
    ok: true,
    message: {
      type: 'OFFLINE_PROGRESSION',
      playerId: message.playerId,
      offlineDurationMillis: message.offlineDurationMillis,
      kills: message.kills,
      experienceGained: message.experienceGained,
      goldGained: message.goldGained,
      playerLevel: message.playerLevel,
      playerLevelsGained: message.playerLevelsGained,
      zoneId: message.zoneId,
      zoneLevel: message.zoneLevel,
      zoneLevelsGained: message.zoneLevelsGained,
      rewards: message.rewards,
      serverTime: message.serverTime,
    } satisfies OfflineProgressionMessage,
  }
}

function parseCommandErrorMessage(message: Record<string, unknown>): SocketMessageParseResult {
  if (!isString(message.playerId) || !isString(message.commandType) || !isString(message.message) || !isString(message.serverTime)) {
    return invalidMessage('INVALID_MESSAGE_SHAPE', 'COMMAND_ERROR payload is missing required fields.')
  }
  return {
    ok: true,
    message: {
      type: 'COMMAND_ERROR',
      playerId: message.playerId,
      commandType: message.commandType,
      message: message.message,
      serverTime: message.serverTime,
    } satisfies CommandErrorMessage,
  }
}

function isPlayerStateSnapshot(value: unknown): value is PlayerStateSnapshot {
  return (
    isRecord(value) &&
    isString(value.playerId) &&
    isString(value.playerName) &&
    isNumber(value.playerExperience) &&
    isNumber(value.playerLevel) &&
    isNumber(value.playerGold) &&
    isNumber(value.playerEssence) &&
    isOwnedCharacterSnapshotArray(value.ownedCharacters) &&
    isZoneProgressSnapshotArray(value.zoneProgress) &&
    isInventoryItemSnapshotArray(value.inventory) &&
    isString(value.serverTime)
  )
}

function isNullableCombatSnapshot(value: unknown): value is CombatSnapshot | null {
  return value === null || isCombatSnapshot(value)
}

function isCombatSnapshot(value: unknown): value is CombatSnapshot {
  return (
    isRecord(value) &&
    isString(value.playerId) &&
    isString(value.status) &&
    isNullableString(value.zoneId) &&
    isNullableString(value.activeTeamId) &&
    isNullableString(value.enemyName) &&
    isNullableString(value.enemyImage) &&
    isNumber(value.enemyAttack) &&
    isNumber(value.enemyHp) &&
    isNumber(value.enemyMaxHp) &&
    isNumber(value.teamDps) &&
    isNumber(value.pendingReviveMillis) &&
    isCombatMemberArray(value.members)
  )
}

function isOwnedCharacterSnapshotArray(value: unknown): value is OwnedCharacterSnapshot[] {
  return Array.isArray(value) && value.every(isOwnedCharacterSnapshot)
}

function isOwnedCharacterSnapshot(value: unknown): value is OwnedCharacterSnapshot {
  return isRecord(value) && isString(value.key) && isString(value.name) && isNumber(value.level)
}

function isZoneProgressSnapshotArray(value: unknown): value is ZoneProgressSnapshot[] {
  return Array.isArray(value) && value.every(isZoneProgressSnapshot)
}

function isZoneProgressSnapshot(value: unknown): value is ZoneProgressSnapshot {
  return isRecord(value) && isString(value.zoneId) && isNumber(value.killCount) && isNumber(value.level)
}

function isInventoryItemSnapshotArray(value: unknown): value is InventoryItemSnapshot[] {
  return Array.isArray(value) && value.every(isInventoryItemSnapshot)
}

function isInventoryItemSnapshot(value: unknown): value is InventoryItemSnapshot {
  return (
    isRecord(value) &&
    isString(value.id) &&
    isString(value.itemName) &&
    isString(value.itemDisplayName) &&
    isString(value.itemType) &&
    isNumber(value.itemLevel) &&
    isStat(value.itemBaseStat) &&
    isStringArray(value.itemSubStatPool) &&
    isStatArray(value.subStats) &&
    isString(value.rarity) &&
    isNumber(value.upgrade) &&
    isNullableString(value.equippedTeamId) &&
    isNullableNumber(value.equippedPosition)
  )
}

function isCombatMemberArray(value: unknown): value is CombatMember[] {
  return Array.isArray(value) && value.every(isCombatMember)
}

function isCombatMember(value: unknown): value is CombatMember {
  return (
    isRecord(value) &&
    isString(value.characterKey) &&
    isNumber(value.attack) &&
    isNumber(value.hit) &&
    isNumber(value.currentHp) &&
    isNumber(value.maxHp) &&
    isBoolean(value.alive)
  )
}

function isOfflineRewardSummaryArray(value: unknown): value is OfflineRewardSummary[] {
  return Array.isArray(value) && value.every(isOfflineRewardSummary)
}

function isOfflineRewardSummary(value: unknown): value is OfflineRewardSummary {
  return isRecord(value) && isString(value.itemName) && isNumber(value.count)
}

function isStatArray(value: unknown): value is Stat[] {
  return Array.isArray(value) && value.every(isStat)
}

function isStat(value: unknown): value is Stat {
  return isRecord(value) && isString(value.type) && isNumber(value.value)
}

function isStringArray(value: unknown): value is string[] {
  return Array.isArray(value) && value.every(isString)
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

function isString(value: unknown): value is string {
  return typeof value === 'string'
}

function isNullableString(value: unknown): value is string | null {
  return value === null || isString(value)
}

function isNumber(value: unknown): value is number {
  return typeof value === 'number' && Number.isFinite(value)
}

function isNullableNumber(value: unknown): value is number | null {
  return value === null || isNumber(value)
}

function isBoolean(value: unknown): value is boolean {
  return typeof value === 'boolean'
}

function invalidMessage(code: SocketMessageParseError['code'], message: string): SocketMessageParseResult {
  return { ok: false, error: { code, message } }
}
