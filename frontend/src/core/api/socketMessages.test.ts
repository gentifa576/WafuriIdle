import { describe, expect, it } from 'vitest'
import { parsePlayerSocketMessage } from './socketMessages'

describe('parsePlayerSocketMessage', () => {
  it('parses supported socket payloads', () => {
    const parsed = parsePlayerSocketMessage(
      JSON.stringify({
        type: 'PLAYER_STATE_SYNC',
        playerId: 'player-1',
        snapshot: {
          playerId: 'player-1',
          playerName: 'Scout',
          playerExperience: 120,
          playerLevel: 2,
          playerGold: 90,
          playerEssence: 4,
          ownedCharacters: [{ key: 'hero', name: 'Hero', level: 2 }],
          zoneProgress: [{ zoneId: 'starter-plains', killCount: 6, level: 2 }],
          inventory: [],
          serverTime: '2099-01-01T00:00:00Z',
        },
      }),
    )

    expect(parsed).toEqual({
      type: 'PLAYER_STATE_SYNC',
      playerId: 'player-1',
      snapshot: {
        playerId: 'player-1',
        playerName: 'Scout',
        playerExperience: 120,
        playerLevel: 2,
        playerGold: 90,
        playerEssence: 4,
        ownedCharacters: [{ key: 'hero', name: 'Hero', level: 2 }],
        zoneProgress: [{ zoneId: 'starter-plains', killCount: 6, level: 2 }],
        inventory: [],
        serverTime: '2099-01-01T00:00:00Z',
      },
    })
  })

  it('rejects malformed or unsupported payloads', () => {
    expect(parsePlayerSocketMessage('not json')).toBeNull()
    expect(parsePlayerSocketMessage(JSON.stringify({ type: 'PLAYER_STATE_SYNC', playerId: 'player-1' }))).toBeNull()
    expect(parsePlayerSocketMessage(JSON.stringify({ type: 'UNKNOWN_EVENT' }))).toBeNull()
  })
})
