import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { TeamWorkspace } from './TeamWorkspace'

const teams = [
  {
    id: 'team-1',
    playerId: 'player-1',
    slots: [
      { position: 1, characterKey: 'hero', weaponItemId: 'weapon-1', armorItemId: null, accessoryItemId: null },
      { position: 2, characterKey: null, weaponItemId: null, armorItemId: null, accessoryItemId: null },
      { position: 3, characterKey: 'mage', weaponItemId: null, armorItemId: 'armor-1', accessoryItemId: null },
    ],
    characterKeys: ['hero', 'mage'],
    occupiedSlots: 2,
    shortLabel: 'team-1',
  },
  {
    id: 'team-2',
    playerId: 'player-1',
    slots: [
      { position: 1, characterKey: null, weaponItemId: null, armorItemId: null, accessoryItemId: null },
      { position: 2, characterKey: null, weaponItemId: null, armorItemId: null, accessoryItemId: null },
      { position: 3, characterKey: null, weaponItemId: null, armorItemId: null, accessoryItemId: null },
    ],
    characterKeys: [],
    occupiedSlots: 0,
    shortLabel: 'team-2',
  },
]

const inventory = [
  {
    id: 'weapon-1',
    itemName: 'iron-blade',
    itemDisplayName: 'Iron Blade',
    itemType: 'WEAPON',
    itemLevel: 5,
    itemBaseStat: { type: 'ATK', value: 10 },
    itemSubStatPool: [],
    subStats: [],
    rarity: 'COMMON',
    upgrade: 0,
    equippedTeamId: 'team-1',
    equippedPosition: 1,
    assignmentLabel: 'Equipped on team-1 · Slot 1',
  },
  {
    id: 'armor-1',
    itemName: 'silk-robe',
    itemDisplayName: 'Silk Robe',
    itemType: 'ARMOR',
    itemLevel: 3,
    itemBaseStat: { type: 'HP', value: 8 },
    itemSubStatPool: [],
    subStats: [],
    rarity: 'COMMON',
    upgrade: 0,
    equippedTeamId: 'team-1',
    equippedPosition: 3,
    assignmentLabel: 'Equipped on team-1 · Slot 3',
  },
  {
    id: 'weapon-2',
    itemName: 'bronze-spear',
    itemDisplayName: 'Bronze Spear',
    itemType: 'WEAPON',
    itemLevel: 2,
    itemBaseStat: { type: 'ATK', value: 6 },
    itemSubStatPool: [],
    subStats: [],
    rarity: 'COMMON',
    upgrade: 0,
    equippedTeamId: null,
    equippedPosition: null,
    assignmentLabel: 'In backpack',
  },
  {
    id: 'weapon-3',
    itemName: 'crystal-bow',
    itemDisplayName: 'Crystal Bow',
    itemType: 'WEAPON',
    itemLevel: 9,
    itemBaseStat: { type: 'ATK', value: 16 },
    itemSubStatPool: [],
    subStats: [],
    rarity: 'RARE',
    upgrade: 0,
    equippedTeamId: null,
    equippedPosition: null,
    assignmentLabel: 'In backpack',
  },
]

const ownedCharacters = [
  { key: 'hero', name: 'Hero', level: 10, label: 'Hero · Lv.10' },
  { key: 'mage', name: 'Mage', level: 10, label: 'Mage · Lv.10' },
  { key: 'archer', name: 'Archer', level: 10, label: 'Archer · Lv.10' },
]

const templates = [
  {
    key: 'hero',
    name: 'Hero',
    image: '/assets/characters/hero/front.png',
    tags: ['warrior'],
    strength: { base: 10, increment: 1 },
    agility: { base: 8, increment: 0.8 },
    intelligence: { base: 4, increment: 0.2 },
    wisdom: { base: 3, increment: 0.2 },
    vitality: { base: 12, increment: 1.1 },
    skill: { key: 'slash', name: 'Slash', cooldownMillis: 4000 },
    passive: { key: 'valor', name: 'Valor', leaderOnly: true, trigger: 'ALWAYS', condition: { type: 'ALWAYS' } },
  },
  {
    key: 'mage',
    name: 'Mage',
    image: '/assets/characters/mage/front.png',
    tags: ['caster'],
    strength: { base: 4, increment: 0.2 },
    agility: { base: 6, increment: 0.4 },
    intelligence: { base: 12, increment: 1.1 },
    wisdom: { base: 10, increment: 0.9 },
    vitality: { base: 7, increment: 0.5 },
    skill: { key: 'spark', name: 'Spark', cooldownMillis: 5000 },
    passive: null,
  },
  {
    key: 'archer',
    name: 'Archer',
    image: '/assets/characters/archer/front.png',
    tags: ['ranged'],
    strength: { base: 7, increment: 0.6 },
    agility: { base: 11, increment: 1 },
    intelligence: { base: 5, increment: 0.3 },
    wisdom: { base: 5, increment: 0.3 },
    vitality: { base: 8, increment: 0.6 },
    skill: null,
    passive: null,
  },
]

describe('TeamWorkspace', () => {
  it('shows team list first with empty slots and gear summaries', () => {
    render(
      <TeamWorkspace
        activeTeam={teams[0]}
        activeTeamId="team-1"
        inventory={inventory}
        loading={false}
        onActivateTeam={vi.fn()}
        onSaveTeam={vi.fn()}
        onTeamChange={vi.fn()}
        ownedCharacterNames={new Map([
          ['hero', 'Hero'],
          ['mage', 'Mage'],
        ])}
        ownedCharacters={ownedCharacters}
        templates={templates}
        selectedTeam={teams[0]}
        teams={teams}
      />,
    )

    expect(screen.getByRole('heading', { name: 'Team List' })).toBeInTheDocument()
    expect(screen.getAllByText('Empty').length).toBeGreaterThan(0)
    expect(screen.getAllByLabelText(/Weapon equipped:|Weapon empty/).length).toBeGreaterThan(0)
    expect(screen.getAllByText('Active').length).toBeGreaterThan(0)
    expect(screen.getByRole('button', { name: 'Edit team 1' })).toHaveClass('team-overview-card')
  })

  it('opens the existing editor after selecting a team card', async () => {
    const user = userEvent.setup()
    const onTeamChange = vi.fn()

    render(
      <TeamWorkspace
        activeTeam={teams[0]}
        activeTeamId="team-1"
        inventory={inventory}
        loading={false}
        onActivateTeam={vi.fn()}
        onSaveTeam={vi.fn()}
        onTeamChange={onTeamChange}
        ownedCharacterNames={new Map([
          ['hero', 'Hero'],
          ['mage', 'Mage'],
        ])}
        ownedCharacters={ownedCharacters}
        templates={templates}
        selectedTeam={teams[0]}
        teams={teams}
      />,
    )

    await user.click(screen.getByRole('button', { name: 'Edit team 1' }))

    expect(onTeamChange).toHaveBeenCalledWith('team-1')
    expect(screen.getByRole('heading', { name: 'Team Editor' })).toBeInTheDocument()
    expect(screen.getByText('Editing')).toBeInTheDocument()
    expect(screen.getAllByText('team-1').length).toBeGreaterThan(0)
    expect(screen.queryByText('Editing team')).not.toBeInTheDocument()
    const slotStats = within(screen.getByLabelText('Combined stats for slot 1'))
    expect(slotStats.getByText('STR')).toBeInTheDocument()
    expect(slotStats.getByText('ATK')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Back' })).toBeInTheDocument()
  })

  it('does not open the editor when setting a team active', async () => {
    const user = userEvent.setup()
    const onActivateTeam = vi.fn()
    const onTeamChange = vi.fn()

    render(
      <TeamWorkspace
        activeTeam={teams[1]}
        activeTeamId="team-2"
        inventory={inventory}
        loading={false}
        onActivateTeam={onActivateTeam}
        onSaveTeam={vi.fn()}
        onTeamChange={onTeamChange}
        ownedCharacterNames={new Map([
          ['hero', 'Hero'],
          ['mage', 'Mage'],
        ])}
        ownedCharacters={ownedCharacters}
        templates={templates}
        selectedTeam={teams[0]}
        teams={teams}
      />,
    )

    await user.click(screen.getByRole('button', { name: 'Set Active' }))

    expect(onActivateTeam).toHaveBeenCalledWith('team-1')
    expect(onTeamChange).not.toHaveBeenCalled()
    expect(screen.getByRole('heading', { name: 'Team List' })).toBeInTheDocument()
  })

  it('orders character selection with the current slot character first, then alphabetical', async () => {
    const user = userEvent.setup()

    render(
      <TeamWorkspace
        activeTeam={teams[0]}
        activeTeamId="team-1"
        inventory={inventory}
        loading={false}
        onActivateTeam={vi.fn()}
        onSaveTeam={vi.fn()}
        onTeamChange={vi.fn()}
        ownedCharacterNames={new Map([
          ['hero', 'Hero'],
          ['mage', 'Mage'],
          ['archer', 'Archer'],
        ])}
        ownedCharacters={ownedCharacters}
        templates={templates}
        selectedTeam={teams[0]}
        teams={teams}
      />,
    )

    await user.click(screen.getByRole('button', { name: 'Edit team 1' }))
    await user.click(screen.getByRole('button', { name: 'Choose character for slot 1' }))

    const characterButtons = screen.getAllByRole('button').filter((button) =>
      ['View Hero', 'View Archer'].includes(button.getAttribute('aria-label') ?? ''),
    )

    expect(characterButtons.map((button) => button.getAttribute('aria-label'))).toEqual(['View Hero', 'View Archer'])
  })

  it('orders equipment selection with empty first, current item second, then remaining items by level', async () => {
    const user = userEvent.setup()

    render(
      <TeamWorkspace
        activeTeam={teams[0]}
        activeTeamId="team-1"
        inventory={inventory}
        loading={false}
        onActivateTeam={vi.fn()}
        onSaveTeam={vi.fn()}
        onTeamChange={vi.fn()}
        ownedCharacterNames={new Map([
          ['hero', 'Hero'],
          ['mage', 'Mage'],
          ['archer', 'Archer'],
        ])}
        ownedCharacters={ownedCharacters}
        templates={templates}
        selectedTeam={teams[0]}
        teams={teams}
      />,
    )

    await user.click(screen.getByRole('button', { name: 'Edit team 1' }))
    await user.click(screen.getByRole('button', { name: 'Choose weapon for slot 1' }))

    const equipmentButtons = screen.getAllByRole('button').filter((button) =>
      ['Leave weapon empty for slot 1', 'View Iron Blade', 'View Crystal Bow', 'View Bronze Spear'].includes(button.getAttribute('aria-label') ?? ''),
    )

    expect(equipmentButtons.map((button) => button.getAttribute('aria-label'))).toEqual([
      'Leave weapon empty for slot 1',
      'View Iron Blade',
      'View Crystal Bow',
      'View Bronze Spear',
    ])
  })

  it('keeps changes local until save and discards draft when leaving without save', async () => {
    const user = userEvent.setup()
    const onSaveTeam = vi.fn()

    render(
      <TeamWorkspace
        activeTeam={teams[0]}
        activeTeamId="team-1"
        inventory={inventory}
        loading={false}
        onActivateTeam={vi.fn()}
        onSaveTeam={onSaveTeam}
        onTeamChange={vi.fn()}
        ownedCharacterNames={new Map([
          ['hero', 'Hero'],
          ['mage', 'Mage'],
          ['archer', 'Archer'],
        ])}
        ownedCharacters={ownedCharacters}
        templates={templates}
        selectedTeam={teams[0]}
        teams={teams}
      />,
    )

    await user.click(screen.getByRole('button', { name: 'Edit team 1' }))
    await user.click(screen.getByRole('button', { name: 'Choose weapon for slot 1' }))
    await user.click(screen.getByRole('button', { name: 'View Crystal Bow' }))

    expect(onSaveTeam).not.toHaveBeenCalled()
    expect(screen.getByRole('button', { name: 'Save Team' })).toBeEnabled()

    await user.click(screen.getByRole('button', { name: 'Back' }))
    await user.click(screen.getByRole('button', { name: 'Edit team 1' }))
    expect(screen.getByRole('button', { name: 'Save Team' })).toBeDisabled()
  })
})
