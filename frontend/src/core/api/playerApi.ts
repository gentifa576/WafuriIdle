import { http } from './httpClient'
import type {
  AuthResponse,
  CharacterPullResult,
  CharacterTemplate,
  InventoryItemSnapshot,
  Player,
  Team,
} from '../types/api'

export function createGuestPlayer(name: string) {
  return http<AuthResponse>('/auth/signup', {
    method: 'POST',
    body: JSON.stringify({
      name,
      email: null,
      password: null,
    }),
  })
}

export function signUpPlayer(name: string, email: string | null, password: string) {
  return http<AuthResponse>('/auth/signup', {
    method: 'POST',
    body: JSON.stringify({
      name,
      email,
      password,
    }),
  })
}

export function loginPlayer(identity: { name?: string; email?: string; password: string }) {
  return http<AuthResponse>('/auth/login', {
    method: 'POST',
    body: JSON.stringify({
      name: identity.name ?? null,
      email: identity.email ?? null,
      password: identity.password,
    }),
  })
}

export function logoutPlayer() {
  return http<void>('/auth/logout', {
    method: 'POST',
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

export function getStarterCharacterTemplates() {
  return http<CharacterTemplate[]>('/characters/starters')
}

export function claimStarterCharacter(playerId: string, characterKey: string) {
  return http<void>(`/players/${playerId}/starter`, {
    method: 'POST',
    body: JSON.stringify({ characterKey }),
  })
}

export function pullCharacter(playerId: string, count = 1) {
  return http<CharacterPullResult>(`/players/${playerId}/gacha/characters/pull`, {
    method: 'POST',
    body: JSON.stringify({ count }),
  })
}
