export interface Player {
  id: string
  name: string
  ownedCharacterKeys: string[]
  activeTeamId: string | null
}

export interface Team {
  id: string
  playerId: string
  characterKeys: string[]
}

export interface CharacterTemplate {
  key: string
  name: string
}

export interface OwnedCharacterSnapshot {
  key: string
  name: string
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
  equippedCharacterKey: string | null
}

export interface CombatMember {
  characterKey: string
  attack: number
  hit: number
  currentHp: number
  maxHp: number
}

export interface CombatSnapshot {
  playerId: string
  status: string
  zoneId: string
  activeTeamId: string
  enemyName: string
  enemyHp: number
  enemyMaxHp: number
  teamDps: number
  members: CombatMember[]
}

export interface PlayerStateSyncMessage {
  type: 'PLAYER_STATE_SYNC'
  playerId: string
  snapshot: {
    playerId: string
    playerName: string
    ownedCharacters: OwnedCharacterSnapshot[]
    inventory: InventoryItemSnapshot[]
    serverTime: string
  }
}

export interface CombatStateSyncMessage {
  type: 'COMBAT_STATE_SYNC'
  playerId: string
  snapshot: CombatSnapshot | null
  serverTime: string
}

export type PlayerSocketMessage = PlayerStateSyncMessage | CombatStateSyncMessage
