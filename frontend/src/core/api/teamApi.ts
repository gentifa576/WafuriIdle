import { http } from './httpClient'
import type { Team } from '../types/api'

export function assignCharacterToTeam(teamId: string, characterKey: string) {
  return http<Team>(`/teams/${teamId}/characters/${characterKey}`, {
    method: 'POST',
  })
}

export function activateTeam(teamId: string) {
  return http<Team>(`/teams/${teamId}/activate`, {
    method: 'POST',
  })
}
