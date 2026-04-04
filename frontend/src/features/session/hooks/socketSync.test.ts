import { describe, expect, it } from 'vitest'
import { applyCombatStateSnapshot, applyPlayerStateSnapshot } from './socketSync'
import type { CombatSnapshot, PlayerStateSnapshot } from '../../../core/types/api'
import { mapCombat, mapInventory, mapOwnedCharacters, mapPlayer, mapZoneProgress } from '../model/clientModels'

describe('socketSync', () => {
  it('reuses current references when a player snapshot is equivalent', () => {
    const player = mapPlayer({
      id: 'player-1',
      name: 'Scout',
      ownedCharacterKeys: ['hero'],
      activeTeamId: 'team-1',
      experience: 120,
      level: 2,
      gold: 90,
      essence: 4,
    })
    const inventory = mapInventory([])
    const ownedCharacters = mapOwnedCharacters([{ key: 'hero', name: 'Hero', level: 2 }])
    const zoneProgress = mapZoneProgress([{ zoneId: 'starter-plains', killCount: 6, level: 2 }])
    const snapshot: PlayerStateSnapshot = {
      playerId: 'player-1',
      playerName: 'Scout',
      playerExperience: 120,
      playerLevel: 2,
      playerGold: 90,
      playerEssence: 4,
      ownedCharacters,
      zoneProgress,
      inventory,
      serverTime: '2099-01-01T00:00:00Z',
    }

    const result = applyPlayerStateSnapshot(player, inventory, ownedCharacters, zoneProgress, snapshot)

    expect(result.player).toBe(player)
    expect(result.inventory).toBe(inventory)
    expect(result.ownedCharacters).toBe(ownedCharacters)
    expect(result.zoneProgress).toBe(zoneProgress)
  })

  it('replaces the combat snapshot when combat data changes', () => {
    const currentTransport: CombatSnapshot = {
      playerId: 'player-1',
      status: 'FIGHTING',
      zoneId: 'starter-plains',
      activeTeamId: 'team-1',
      enemyName: 'Training Slime',
      enemyAttack: 1,
      enemyHp: 18,
      enemyMaxHp: 20,
      teamDps: 5.5,
      pendingReviveMillis: 0,
      members: [{ characterKey: 'hero', attack: 10, hit: 0.55, currentHp: 23, maxHp: 24, alive: true }],
    }
    const current = mapCombat(currentTransport)
    const next: CombatSnapshot = {
      playerId: 'player-1',
      status: 'FIGHTING',
      zoneId: 'starter-plains',
      activeTeamId: 'team-1',
      enemyName: 'Training Slime',
      enemyAttack: 1,
      enemyHp: 12,
      enemyMaxHp: 20,
      teamDps: 5.5,
      pendingReviveMillis: 0,
      members: [{ characterKey: 'hero', attack: 10, hit: 0.55, currentHp: 23, maxHp: 24, alive: true }],
    }

    expect(applyCombatStateSnapshot(current, next)).toEqual(mapCombat(next))
  })
})
