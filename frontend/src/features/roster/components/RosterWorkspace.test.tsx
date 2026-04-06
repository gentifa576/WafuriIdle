import { act, fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { RosterWorkspace } from './RosterWorkspace'

const ownedCharacters = [
  { key: 'hero', name: 'Hero', level: 12, label: 'Hero · Lv.12' },
  { key: 'mage', name: 'Mage', level: 12, label: 'Mage · Lv.12' },
]

const templates = [
  {
    key: 'hero',
    name: 'Hero',
    strength: { base: 12, increment: 1.2 },
    agility: { base: 8, increment: 0.8 },
    intelligence: { base: 5, increment: 0.4 },
    wisdom: { base: 4, increment: 0.3 },
    vitality: { base: 14, increment: 1.6 },
    image: '/assets/characters/hero/front.png',
    tags: ['leader', 'sword'],
    skill: { key: 'hero-slash', name: 'Hero Slash', cooldownMillis: 7200 },
    passive: {
      key: 'hero-aura',
      name: 'Leader Aura',
      leaderOnly: true,
      trigger: 'AURA',
      condition: { type: 'ALWAYS' },
    },
  },
  {
    key: 'mage',
    name: 'Mage',
    strength: { base: 4, increment: 0.2 },
    agility: { base: 6, increment: 0.5 },
    intelligence: { base: 13, increment: 1.5 },
    wisdom: { base: 11, increment: 1.2 },
    vitality: { base: 8, increment: 0.7 },
    image: null,
    tags: ['caster'],
    skill: null,
    passive: null,
  },
]

afterEach(() => {
  vi.useRealTimers()
})

describe('RosterWorkspace', () => {
  it('shows the hover preview after a 400ms delay', () => {
    vi.useFakeTimers()

    render(<RosterWorkspace ownedCharacters={ownedCharacters} templates={templates} />)

    fireEvent.mouseEnter(screen.getByRole('button', { name: 'View Hero' }), {
      clientX: 120,
      clientY: 140,
    })

    act(() => {
      vi.advanceTimersByTime(399)
    })
    expect(screen.queryByText('Preview')).not.toBeInTheDocument()

    act(() => {
      vi.advanceTimersByTime(1)
    })
    expect(screen.getByText('Preview')).toBeInTheDocument()
    expect(screen.getByText(/Skill: Hero Slash \| Passive: Leader Aura/)).toBeInTheDocument()
  })

  it('opens and closes the selected character detail view', async () => {
    const user = userEvent.setup()

    render(<RosterWorkspace ownedCharacters={ownedCharacters} templates={templates} />)

    await user.click(screen.getByRole('button', { name: 'View Hero' }))

    expect(screen.getByRole('button', { name: 'Back' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Hero Slash' })).toBeInTheDocument()
    expect(screen.getByText('Character Details')).toBeInTheDocument()
    expect(screen.getByText('Level 12')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Back' }))

    expect(screen.getByRole('heading', { name: 'Character Unlocks' })).toBeInTheDocument()
  })
})
