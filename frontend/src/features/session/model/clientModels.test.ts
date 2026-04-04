import { describe, expect, it } from 'vitest'
import type { InventoryItem } from '../../../core/types/api'
import { mapRestInventory } from './clientModels'

describe('clientModels', () => {
  it('maps REST inventory payloads with nested item data', () => {
    const items: InventoryItem[] = [
      {
        id: 'inventory-1',
        playerId: 'player-1',
        item: {
          name: 'rusty-sword',
          displayName: 'Rusty Sword',
          type: 'WEAPON',
          baseStat: { type: 'ATTACK', value: 7 },
          subStatPool: ['HIT', 'HP'],
        },
        itemLevel: 3,
        subStats: [{ type: 'HIT', value: 2 }],
        rarity: 'COMMON',
        upgrade: 0,
        equippedTeamId: 'team-12345678',
        equippedPosition: 2,
      },
    ]

    expect(mapRestInventory(items)).toEqual([
      {
        id: 'inventory-1',
        itemName: 'rusty-sword',
        itemDisplayName: 'Rusty Sword',
        itemType: 'WEAPON',
        itemBaseStat: { type: 'ATTACK', value: 7 },
        itemSubStatPool: ['HIT', 'HP'],
        subStats: [{ type: 'HIT', value: 2 }],
        rarity: 'COMMON',
        upgrade: 0,
        equippedTeamId: 'team-12345678',
        equippedPosition: 2,
        assignmentLabel: 'Equipped on team-123 · Slot 2',
      },
    ])
  })
})
