import { http } from './httpClient'
import type {
  CharacterTemplate,
  CombatSnapshot,
  InventoryItemSnapshot,
  Player,
  Team,
} from '../types/api'

export function createPlayer(name: string) {
  return http<Player>('/players', {
    method: 'POST',
    body: JSON.stringify({ name }),
  })
}

export function getPlayer(playerId: string) {
  return http<Player>(`/players/${playerId}`)
}

export function getPlayerTeams(playerId: string) {
  return http<Team[]>(`/players/${playerId}/teams`)
}

export function getPlayerInventory(playerId: string) {
  return http<InventoryItemSnapshot[]>(`/players/${playerId}/inventory`)
}

export function getCharacterTemplates() {
  return http<CharacterTemplate[]>('/characters/templates')
}

export function startCombat(playerId: string) {
  return http<CombatSnapshot>(`/players/${playerId}/combat/start`, {
    method: 'POST',
  })
}
