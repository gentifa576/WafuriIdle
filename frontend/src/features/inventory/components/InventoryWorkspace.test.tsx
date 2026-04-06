import { act, fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { InventoryWorkspace } from './InventoryWorkspace'

const inventory = [
  {
    id: 'accessory-1',
    itemName: 'lucky-charm',
    itemDisplayName: 'Lucky Charm',
    itemType: 'ACCESSORY',
    itemLevel: 18,
    itemBaseStat: { type: 'HP', value: 12 },
    itemSubStatPool: [],
    subStats: [{ type: 'HIT', value: 3 }],
    rarity: 'RARE',
    upgrade: 1,
    equippedTeamId: null,
    equippedPosition: null,
    assignmentLabel: 'In backpack',
  },
  {
    id: 'weapon-1',
    itemName: 'iron-blade',
    itemDisplayName: 'Iron Blade',
    itemType: 'WEAPON',
    itemLevel: 22,
    itemBaseStat: { type: 'ATTACK', value: 24 },
    itemSubStatPool: [],
    subStats: [{ type: 'HIT', value: 5 }],
    rarity: 'EPIC',
    upgrade: 3,
    equippedTeamId: 'team-1',
    equippedPosition: 1,
    assignmentLabel: 'Equipped on team-1 · Slot 1',
  },
  {
    id: 'armor-1',
    itemName: 'iron-mail',
    itemDisplayName: 'Iron Mail',
    itemType: 'ARMOR',
    itemLevel: 22,
    itemBaseStat: { type: 'HP', value: 30 },
    itemSubStatPool: [],
    subStats: [],
    rarity: 'COMMON',
    upgrade: 0,
    equippedTeamId: null,
    equippedPosition: null,
    assignmentLabel: 'In backpack',
  },
]

afterEach(() => {
  vi.useRealTimers()
})

describe('InventoryWorkspace', () => {
  it('sorts equipped items first, then level, then type priority', () => {
    render(<InventoryWorkspace inventory={inventory} />)

    const tiles = screen.getAllByRole('button', { name: /View / })
    expect(tiles.map((tile) => tile.getAttribute('aria-label'))).toEqual(['View Iron Blade', 'View Iron Mail', 'View Lucky Charm'])
  })

  it('shows hover preview after 400ms', () => {
    vi.useFakeTimers()

    render(<InventoryWorkspace inventory={inventory} />)

    fireEvent.mouseEnter(screen.getByRole('button', { name: 'View Iron Blade' }), {
      clientX: 120,
      clientY: 140,
    })

    act(() => {
      vi.advanceTimersByTime(400)
    })

    expect(screen.getByText('Preview')).toBeInTheDocument()
    expect(screen.getByText(/Equipped on team-1/i)).toBeInTheDocument()
  })

  it('opens detail view on click', async () => {
    const user = userEvent.setup()

    render(<InventoryWorkspace inventory={inventory} />)

    await user.click(screen.getByRole('button', { name: 'View Iron Blade' }))

    expect(screen.getByRole('button', { name: 'Back' })).toBeInTheDocument()
    expect(screen.getByText('Item Details')).toBeInTheDocument()
    expect(screen.getByText(/Level 22 epic weapon/i)).toBeInTheDocument()
  })
})
