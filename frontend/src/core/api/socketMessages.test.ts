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
      ok: true,
      message: {
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
      },
    })

    const skillEventParsed = parsePlayerSocketMessage(
      JSON.stringify({
        type: 'SKILL_EVENTS',
        playerId: 'player-1',
        events: [
          {
            eventId: 'event-1',
            characterKey: 'hero',
            skillKey: 'power-slash',
            effectType: 'DAMAGE',
            targetType: 'ENEMY',
            value: 128.5,
            statusKey: null,
            targetKey: null,
            durationMillis: null,
          },
        ],
        serverTime: '2099-01-01T00:00:01Z',
      }),
    )

    expect(skillEventParsed).toEqual({
      ok: true,
      message: {
        type: 'SKILL_EVENTS',
        playerId: 'player-1',
        events: [
          {
            eventId: 'event-1',
            characterKey: 'hero',
            skillKey: 'power-slash',
            effectType: 'DAMAGE',
            targetType: 'ENEMY',
            value: 128.5,
            statusKey: null,
            targetKey: null,
            durationMillis: null,
          },
        ],
        serverTime: '2099-01-01T00:00:01Z',
      },
    })
  })

  it('rejects malformed or unsupported payloads', () => {
    expect(parsePlayerSocketMessage('not json')).toEqual({
      ok: false,
      error: {
        code: 'INVALID_JSON',
        message: 'Received invalid JSON from the player socket.',
      },
    })
    expect(
      parsePlayerSocketMessage(
        JSON.stringify({
          type: 'PLAYER_STATE_SYNC',
          playerId: 'player-1',
          snapshot: {
            playerId: 'player-1',
            playerName: 'Scout',
            playerExperience: 120,
            playerLevel: '2',
            playerGold: 90,
            playerEssence: 4,
            ownedCharacters: [],
            zoneProgress: [],
            inventory: [],
            serverTime: '2099-01-01T00:00:00Z',
          },
        }),
      ),
    ).toEqual({
      ok: false,
      error: {
        code: 'INVALID_MESSAGE_SHAPE',
        message: 'PLAYER_STATE_SYNC payload is missing required player snapshot fields.',
      },
    })
    expect(parsePlayerSocketMessage(JSON.stringify({ type: 'UNKNOWN_EVENT' }))).toEqual({
      ok: false,
      error: {
        code: 'UNSUPPORTED_MESSAGE',
        message: 'Unsupported socket message type "UNKNOWN_EVENT".',
      },
    })
  })
})
