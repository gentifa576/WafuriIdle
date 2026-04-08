import { http } from './httpClient'
import type { EquipmentSlot, Team } from '../types/api'

export function assignCharacterToTeam(teamId: string, position: number, characterKey: string) {
  return http<Team>(`/teams/${teamId}/slots/${position}/characters/${characterKey}`, {
    method: 'POST',
  })
}

export function activateTeam(teamId: string) {
  return http<Team>(`/teams/${teamId}/activate`, {
    method: 'POST',
  })
}

export function equipTeamItem(teamId: string, position: number, inventoryItemId: string, slot: EquipmentSlot) {
  return http<void>(`/teams/${teamId}/slots/${position}/equip`, {
    method: 'POST',
    body: JSON.stringify({ inventoryItemId, slot }),
  })
}

export function unequipTeamItem(teamId: string, position: number, slot: EquipmentSlot) {
  return http<void>(`/teams/${teamId}/slots/${position}/unequip`, {
    method: 'POST',
    body: JSON.stringify({ slot }),
  })
}

export interface TeamSlotLoadoutRequest {
  position: number
  characterKey: string | null
  weaponItemId: string | null
  armorItemId: string | null
  accessoryItemId: string | null
}

export function saveTeamLoadout(teamId: string, slots: TeamSlotLoadoutRequest[]) {
  return http<Team>(`/teams/${teamId}/loadout`, {
    method: 'POST',
    body: JSON.stringify({ slots }),
  })
}
