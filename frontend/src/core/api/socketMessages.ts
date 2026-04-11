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
  if (!hasFields(message, { playerId: isString, snapshot: isPlayerStateSnapshot })) {
    return invalidMessage('INVALID_MESSAGE_SHAPE', 'PLAYER_STATE_SYNC payload is missing required player snapshot fields.')
  }
  const playerId = message.playerId as string
  const snapshot = message.snapshot as PlayerStateSnapshot
  return {
    ok: true,
    message: {
      type: 'PLAYER_STATE_SYNC',
      playerId,
      snapshot,
    } satisfies PlayerStateSyncMessage,
  }
}

function parseCombatStateSyncMessage(message: Record<string, unknown>): SocketMessageParseResult {
  if (!hasFields(message, { playerId: isString, serverTime: isString, snapshot: isNullableCombatSnapshot })) {
    return invalidMessage('INVALID_MESSAGE_SHAPE', 'COMBAT_STATE_SYNC payload is missing required combat snapshot fields.')
  }
  const playerId = message.playerId as string
  const serverTime = message.serverTime as string
  const snapshot = message.snapshot as CombatSnapshot | null
  return {
    ok: true,
    message: {
      type: 'COMBAT_STATE_SYNC',
      playerId,
      snapshot,
      serverTime,
    } satisfies CombatStateSyncMessage,
  }
}

function parseSkillEventsMessage(message: Record<string, unknown>): SocketMessageParseResult {
  if (!hasFields(message, { playerId: isString, events: isSkillEffectEventArray, serverTime: isString })) {
    return invalidMessage('INVALID_MESSAGE_SHAPE', 'SKILL_EVENTS payload is missing required fields.')
  }
  const playerId = message.playerId as string
  const events = message.events as SkillEffectEvent[]
  const serverTime = message.serverTime as string
  return {
    ok: true,
    message: {
      type: 'SKILL_EVENTS',
      playerId,
      events,
      serverTime,
    } satisfies SkillEventsMessage,
  }
}

function isSkillEffectEventArray(value: unknown): value is SkillEffectEvent[] {
  return Array.isArray(value) && value.every(isSkillEffectEvent)
}

function isSkillEffectEvent(value: unknown): value is SkillEffectEvent {
  return isRecord(value) && hasFields(value, {
    eventId: isString,
    characterKey: isString,
    skillKey: isString,
    effectType: isSkillEffectType,
    targetType: isSkillTargetType,
    targetKey: isNullableString,
    value: isNullableNumber,
    statusKey: isNullableString,
    durationMillis: isNullableNumber,
  })
}

function isSkillEffectType(value: unknown): boolean {
  return isOneOf(value, ['DAMAGE', 'HEAL', 'BUFF_APPLIED', 'DEBUFF_APPLIED', 'SHIELD'])
}

function isSkillTargetType(value: unknown): boolean {
  return isOneOf(value, ['ENEMY', 'ALLY_TEAM', 'ALLY_MEMBER', 'SELF'])
}

function parseZoneLevelUpMessage(message: Record<string, unknown>): SocketMessageParseResult {
  if (!hasFields(message, { playerId: isString, zoneId: isString, level: isNumber, serverTime: isNullableString })) {
    return invalidMessage('INVALID_MESSAGE_SHAPE', 'ZONE_LEVEL_UP payload is missing required fields.')
  }
  const playerId = message.playerId as string
  const zoneId = message.zoneId as string
  const level = message.level as number
  const serverTime = message.serverTime as string | null
  return {
    ok: true,
    message: {
      type: 'ZONE_LEVEL_UP',
      playerId,
      zoneId,
      level,
      serverTime,
    } satisfies ZoneLevelUpMessage,
  }
}

function parseOfflineProgressionMessage(message: Record<string, unknown>): SocketMessageParseResult {
  if (!hasFields(message, {
    playerId: isString,
    offlineDurationMillis: isNumber,
    kills: isNumber,
    experienceGained: isNumber,
    goldGained: isNumber,
    playerLevel: isNumber,
    playerLevelsGained: isNumber,
    zoneId: isString,
    zoneLevel: isNumber,
    zoneLevelsGained: isNumber,
    rewards: isOfflineRewardSummaryArray,
    serverTime: isNullableString,
  })) {
    return invalidMessage('INVALID_MESSAGE_SHAPE', 'OFFLINE_PROGRESSION payload is missing required fields.')
  }
  const playerId = message.playerId as string
  const offlineDurationMillis = message.offlineDurationMillis as number
  const kills = message.kills as number
  const experienceGained = message.experienceGained as number
  const goldGained = message.goldGained as number
  const playerLevel = message.playerLevel as number
  const playerLevelsGained = message.playerLevelsGained as number
  const zoneId = message.zoneId as string
  const zoneLevel = message.zoneLevel as number
  const zoneLevelsGained = message.zoneLevelsGained as number
  const rewards = message.rewards as OfflineRewardSummary[]
  const serverTime = message.serverTime as string | null
  return {
    ok: true,
    message: {
      type: 'OFFLINE_PROGRESSION',
      playerId,
      offlineDurationMillis,
      kills,
      experienceGained,
      goldGained,
      playerLevel,
      playerLevelsGained,
      zoneId,
      zoneLevel,
      zoneLevelsGained,
      rewards,
      serverTime,
    } satisfies OfflineProgressionMessage,
  }
}

function parseCommandErrorMessage(message: Record<string, unknown>): SocketMessageParseResult {
  if (!hasFields(message, { playerId: isString, commandType: isString, message: isString, serverTime: isString })) {
    return invalidMessage('INVALID_MESSAGE_SHAPE', 'COMMAND_ERROR payload is missing required fields.')
  }
  const playerId = message.playerId as string
  const commandType = message.commandType as string
  const errorMessage = message.message as string
  const serverTime = message.serverTime as string
  return {
    ok: true,
    message: {
      type: 'COMMAND_ERROR',
      playerId,
      commandType,
      message: errorMessage,
      serverTime,
    } satisfies CommandErrorMessage,
  }
}

function isPlayerStateSnapshot(value: unknown): value is PlayerStateSnapshot {
  return isRecord(value) && hasFields(value, {
    playerId: isString,
    playerName: isString,
    playerExperience: isNumber,
    playerLevel: isNumber,
    playerGold: isNumber,
    playerEssence: isNumber,
    ownedCharacters: isOwnedCharacterSnapshotArray,
    zoneProgress: isZoneProgressSnapshotArray,
    inventory: isInventoryItemSnapshotArray,
    serverTime: isString,
  })
}

function isNullableCombatSnapshot(value: unknown): value is CombatSnapshot | null {
  return value === null || isCombatSnapshot(value)
}

function isCombatSnapshot(value: unknown): value is CombatSnapshot {
  return isRecord(value) && hasFields(value, {
    playerId: isString,
    status: isString,
    zoneId: isNullableString,
    activeTeamId: isNullableString,
    enemyName: isNullableString,
    enemyImage: isNullableString,
    enemyAttack: isNumber,
    enemyHp: isNumber,
    enemyMaxHp: isNumber,
    teamDps: isNumber,
    pendingReviveMillis: isNumber,
    members: isCombatMemberArray,
  })
}

function isOwnedCharacterSnapshotArray(value: unknown): value is OwnedCharacterSnapshot[] {
  return Array.isArray(value) && value.every(isOwnedCharacterSnapshot)
}

function isOwnedCharacterSnapshot(value: unknown): value is OwnedCharacterSnapshot {
  return isRecord(value) && hasFields(value, { key: isString, name: isString, level: isNumber })
}

function isZoneProgressSnapshotArray(value: unknown): value is ZoneProgressSnapshot[] {
  return Array.isArray(value) && value.every(isZoneProgressSnapshot)
}

function isZoneProgressSnapshot(value: unknown): value is ZoneProgressSnapshot {
  return isRecord(value) && hasFields(value, { zoneId: isString, killCount: isNumber, level: isNumber })
}

function isInventoryItemSnapshotArray(value: unknown): value is InventoryItemSnapshot[] {
  return Array.isArray(value) && value.every(isInventoryItemSnapshot)
}

function isInventoryItemSnapshot(value: unknown): value is InventoryItemSnapshot {
  return isRecord(value) && hasFields(value, {
    id: isString,
    itemName: isString,
    itemDisplayName: isString,
    itemType: isString,
    itemLevel: isNumber,
    itemBaseStat: isStat,
    itemSubStatPool: isStringArray,
    subStats: isStatArray,
    rarity: isString,
    upgrade: isNumber,
    equippedTeamId: isNullableString,
    equippedPosition: isNullableNumber,
  })
}

function isCombatMemberArray(value: unknown): value is CombatMember[] {
  return Array.isArray(value) && value.every(isCombatMember)
}

function isCombatMember(value: unknown): value is CombatMember {
  return isRecord(value) && hasFields(value, {
    characterKey: isString,
    attack: isNumber,
    hit: isNumber,
    currentHp: isNumber,
    maxHp: isNumber,
    alive: isBoolean,
  })
}

function isOfflineRewardSummaryArray(value: unknown): value is OfflineRewardSummary[] {
  return Array.isArray(value) && value.every(isOfflineRewardSummary)
}

function isOfflineRewardSummary(value: unknown): value is OfflineRewardSummary {
  return isRecord(value) && hasFields(value, { itemName: isString, count: isNumber })
}

function isStatArray(value: unknown): value is Stat[] {
  return Array.isArray(value) && value.every(isStat)
}

function isStat(value: unknown): value is Stat {
  return isRecord(value) && hasFields(value, { type: isString, value: isNumber })
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

function hasFields(
  value: Record<string, unknown>,
  validators: Record<string, (field: unknown) => boolean>,
) {
  return Object.entries(validators).every(([key, validate]) => validate(value[key]))
}

function isOneOf(value: unknown, allowed: readonly string[]) {
  return isString(value) && allowed.includes(value)
}

function invalidMessage(code: SocketMessageParseError['code'], message: string): SocketMessageParseResult {
  return { ok: false, error: { code, message } }
}
