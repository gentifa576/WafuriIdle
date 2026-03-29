export interface Player {
  id: string
  name: string
  ownedCharacterKeys: string[]
  activeTeamId: string | null
  experience: number
  level: number
  gold: number
  essence: number
}

export interface TeamMemberSlot {
  position: number
  characterKey: string | null
  weaponItemId: string | null
  armorItemId: string | null
  accessoryItemId: string | null
}

export type EquipmentSlot = 'WEAPON' | 'ARMOR' | 'ACCESSORY'

export interface Team {
  id: string
  playerId: string
  slots: TeamMemberSlot[]
  characterKeys: string[]
}

export interface CharacterTemplate {
  key: string
  name: string
  image?: string | null
  tags?: string[]
}

export interface OwnedCharacterSnapshot {
  key: string
  name: string
  level: number
}

export interface ZoneProgressSnapshot {
  zoneId: string
  killCount: number
  level: number
}

export interface Stat {
  type: string
  value: number
}

export interface InventoryItemSnapshot {
  id: string
  itemName: string
  itemDisplayName: string
  itemType: string
  itemBaseStat: Stat
  itemSubStatPool: string[]
  subStats: Stat[]
  rarity: string
  upgrade: number
  equippedTeamId: string | null
  equippedPosition: number | null
}

export interface CombatMember {
  characterKey: string
  attack: number
  hit: number
  currentHp: number
  maxHp: number
  alive: boolean
}

export interface CombatSnapshot {
  playerId: string
  status: string
  zoneId: string | null
  activeTeamId: string | null
  enemyName: string | null
  enemyHp: number
  enemyMaxHp: number
  teamDps: number
  members: CombatMember[]
}

export interface PlayerStateSnapshot {
  playerId: string
  playerName: string
  playerExperience: number
  playerLevel: number
  playerGold: number
  playerEssence: number
  ownedCharacters: OwnedCharacterSnapshot[]
  zoneProgress: ZoneProgressSnapshot[]
  inventory: InventoryItemSnapshot[]
  serverTime: string
}

export interface CharacterPullResult {
  player: Player
  pulledCharacterKey: string
  grantedCharacterKey: string | null
  essenceGranted: number
}

export interface AuthResponse {
  player: Player
  sessionToken: string
  sessionExpiresAt: string
  guestAccount: boolean
}

export interface PlayerStateSyncMessage {
  type: 'PLAYER_STATE_SYNC'
  playerId: string
  snapshot: PlayerStateSnapshot
}

export interface CombatStateSyncMessage {
  type: 'COMBAT_STATE_SYNC'
  playerId: string
  snapshot: CombatSnapshot | null
  serverTime: string
}

export interface ZoneLevelUpMessage {
  type: 'ZONE_LEVEL_UP'
  playerId: string
  zoneId: string
  level: number
  serverTime: string | null
}

export interface OfflineRewardSummary {
  itemName: string
  count: number
}

export interface OfflineProgressionMessage {
  type: 'OFFLINE_PROGRESSION'
  playerId: string
  offlineDurationMillis: number
  kills: number
  experienceGained: number
  goldGained: number
  playerLevel: number
  playerLevelsGained: number
  zoneId: string
  zoneLevel: number
  zoneLevelsGained: number
  rewards: OfflineRewardSummary[]
  serverTime: string | null
}

export type PlayerSocketMessage =
  | PlayerStateSyncMessage
  | CombatStateSyncMessage
  | ZoneLevelUpMessage
  | OfflineProgressionMessage
