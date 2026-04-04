import type {
  ClientCharacterTemplate,
  ClientCombat,
  ClientInventoryItem,
  ClientOwnedCharacter,
  ClientPlayer,
  ClientPullResult,
  ClientTeam,
  ClientZoneProgress,
} from '../model/clientModels'

export type SocketStatus = 'disconnected' | 'connecting' | 'connected' | 'error'

export interface HudNotification {
  id: string
  title: string
  detail: string
  tone: 'neutral' | 'success' | 'accent'
  at: string
  mergeGroup?: string
  rangeStart?: number
  rangeEnd?: number
}

export interface ActivityEntry {
  id: string
  label: string
}

export interface PullResult {
  count: number
  pulls: ClientPullResult['pulls']
  totalEssenceGranted: number
  unlockedCount: number
  duplicateCount: number
  pulledCharacterKeys: string[]
}

export interface GameClientViewState {
  player: ClientPlayer | null
  teams: ClientTeam[]
  inventory: ClientInventoryItem[]
  ownedCharacters: ClientOwnedCharacter[]
  zoneProgress: ClientZoneProgress[]
  templates: ClientCharacterTemplate[]
  starterTemplates: ClientCharacterTemplate[]
  combat: ClientCombat | null
}
