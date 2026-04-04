import { act, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { SocketMessageParseError } from '../core/api/socketMessages'
import type { PlayerSocketMessage } from '../core/types/api'
import { CombatScreen } from './CombatScreen'

const mocks = vi.hoisted(() => {
  let sessionPlayerId: string | null = null
  let sessionExpiresAt: string | null = null
  const socket = {
    readyState: 1,
    close: vi.fn(),
    send: vi.fn(),
  }

  const state = {
    socket,
    lastSocketOptions: null as {
      onMessage: (message: PlayerSocketMessage) => void
      onInvalidMessage?: (error: SocketMessageParseError) => void
      onOpen?: () => void
      onClose?: () => void
      onError?: () => void
    } | null,
    currentSessionPlayerId: vi.fn(() => sessionPlayerId),
    currentSessionExpiresAt: vi.fn(() => sessionExpiresAt),
    setSession: vi.fn((auth: { sessionExpiresAt: string } | null, playerId?: string | null) => {
      sessionPlayerId = auth ? (playerId ?? sessionPlayerId) : null
      sessionExpiresAt = auth?.sessionExpiresAt ?? null
    }),
    createGuestPlayer: vi.fn(),
    signUpPlayer: vi.fn(),
    loginPlayer: vi.fn(),
    logoutPlayer: vi.fn(),
    getCharacterTemplates: vi.fn(),
    getStarterCharacterTemplates: vi.fn(),
    getPlayer: vi.fn(),
    getPlayerTeams: vi.fn(),
    getPlayerInventory: vi.fn(),
    claimStarterCharacter: vi.fn(),
    pullCharacter: vi.fn(),
    activateTeam: vi.fn(),
    assignCharacterToTeam: vi.fn(),
    equipTeamItem: vi.fn(),
    unequipTeamItem: vi.fn(),
    createPlayerSocket: vi.fn((_playerId: string, options: { onOpen?: () => void }) => {
      state.lastSocketOptions = options as typeof state.lastSocketOptions
      queueMicrotask(() => options.onOpen?.())
      return state.socket as unknown as WebSocket
    }),
    sendStartCombat: vi.fn((socketArg: WebSocket) => {
      ;(socketArg as unknown as typeof state.socket).send(JSON.stringify({ type: 'START_COMBAT' }))
    }),
    sendStopCombat: vi.fn((socketArg: WebSocket) => {
      ;(socketArg as unknown as typeof state.socket).send(JSON.stringify({ type: 'STOP_COMBAT' }))
    }),
    reset() {
      sessionPlayerId = null
      sessionExpiresAt = null
      this.lastSocketOptions = null
      this.socket.readyState = 1
      this.socket.close.mockReset()
      this.socket.send.mockReset()
      this.currentSessionPlayerId.mockClear()
      this.currentSessionExpiresAt.mockClear()
      this.setSession.mockClear()
      this.createGuestPlayer.mockReset()
      this.signUpPlayer.mockReset()
      this.loginPlayer.mockReset()
      this.logoutPlayer.mockReset()
      this.getCharacterTemplates.mockReset()
      this.getStarterCharacterTemplates.mockReset()
      this.getPlayer.mockReset()
      this.getPlayerTeams.mockReset()
      this.getPlayerInventory.mockReset()
      this.claimStarterCharacter.mockReset()
      this.pullCharacter.mockReset()
      this.activateTeam.mockReset()
      this.assignCharacterToTeam.mockReset()
      this.equipTeamItem.mockReset()
      this.unequipTeamItem.mockReset()
      this.createPlayerSocket.mockClear()
      this.sendStartCombat.mockClear()
      this.sendStopCombat.mockClear()
    },
  }

  return state
})

vi.mock('../core/api/httpClient', () => ({
  currentSessionPlayerId: mocks.currentSessionPlayerId,
  currentSessionExpiresAt: mocks.currentSessionExpiresAt,
  setSession: mocks.setSession,
}))

vi.mock('../core/api/playerApi', () => ({
  createGuestPlayer: mocks.createGuestPlayer,
  signUpPlayer: mocks.signUpPlayer,
  loginPlayer: mocks.loginPlayer,
  logoutPlayer: mocks.logoutPlayer,
  getCharacterTemplates: mocks.getCharacterTemplates,
  getStarterCharacterTemplates: mocks.getStarterCharacterTemplates,
  getPlayer: mocks.getPlayer,
  getPlayerTeams: mocks.getPlayerTeams,
  getPlayerInventory: mocks.getPlayerInventory,
  claimStarterCharacter: mocks.claimStarterCharacter,
  pullCharacter: mocks.pullCharacter,
}))

vi.mock('../core/api/teamApi', () => ({
  activateTeam: mocks.activateTeam,
  assignCharacterToTeam: mocks.assignCharacterToTeam,
  equipTeamItem: mocks.equipTeamItem,
  unequipTeamItem: mocks.unequipTeamItem,
}))

vi.mock('../core/api/wsClient', () => ({
  createPlayerSocket: mocks.createPlayerSocket,
  sendStartCombat: mocks.sendStartCombat,
  sendStopCombat: mocks.sendStopCombat,
}))

vi.mock('../features/combat/components/CombatViewport', () => ({
  CombatViewport: () => <div data-testid="combat-viewport">viewport</div>,
}))

function guestAuthResponse(overrides?: { activeTeamId?: string | null; ownedCharacterKeys?: string[] }) {
  return {
    player: {
      id: 'player-1',
      name: 'Scout',
      ownedCharacterKeys: overrides?.ownedCharacterKeys ?? [],
      activeTeamId: overrides?.activeTeamId ?? null,
      experience: 0,
      level: 1,
      gold: 250,
      essence: 0,
    },
    sessionToken: 'session-token',
    sessionExpiresAt: '2099-01-01T00:00:00Z',
    guestAccount: true,
  }
}

function playerSnapshot() {
  return {
    playerId: 'player-1',
    playerName: 'Scout',
    playerExperience: 0,
    playerLevel: 1,
    playerGold: 250,
    playerEssence: 0,
    ownedCharacters: [{ key: 'hero', name: 'Hero', level: 1 }],
    zoneProgress: [],
    inventory: [],
    serverTime: '2099-01-01T00:00:00Z',
  }
}

function emptyTeam() {
  return {
    id: 'team-1',
    playerId: 'player-1',
    characterKeys: [],
    slots: [
      { position: 1, characterKey: null, weaponItemId: null, armorItemId: null, accessoryItemId: null },
      { position: 2, characterKey: null, weaponItemId: null, armorItemId: null, accessoryItemId: null },
      { position: 3, characterKey: null, weaponItemId: null, armorItemId: null, accessoryItemId: null },
    ],
  }
}

beforeEach(() => {
  mocks.reset()
  mocks.getCharacterTemplates.mockResolvedValue([{ key: 'hero', name: 'Hero' }])
  mocks.getStarterCharacterTemplates.mockResolvedValue([{ key: 'hero', name: 'Hero' }])
})

describe('CombatScreen', () => {
  it('shows the starter picker after guest creation and clears it after claiming a starter', async () => {
    const emptyPlayer = guestAuthResponse()
    const startedPlayer = guestAuthResponse({ ownedCharacterKeys: ['hero'] }).player

    mocks.createGuestPlayer.mockResolvedValue(emptyPlayer)
    mocks.getPlayer.mockResolvedValueOnce(emptyPlayer.player).mockResolvedValueOnce(startedPlayer)
    mocks.getPlayerTeams.mockResolvedValue([emptyTeam()])
    mocks.getPlayerInventory.mockResolvedValue([])
    mocks.claimStarterCharacter.mockResolvedValue(undefined)

    const user = userEvent.setup()
    render(<CombatScreen />)

    await user.type(screen.getByLabelText('Player name'), 'Scout')
    await user.click(screen.getByRole('button', { name: 'Create Guest' }))

    expect(await screen.findByRole('heading', { name: 'Choose Your First Character' })).toBeInTheDocument()
    expect(await screen.findByText('connected')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Confirm Starter' }))

    await waitFor(() => {
      expect(mocks.claimStarterCharacter).toHaveBeenCalledWith('player-1', 'hero')
      expect(screen.queryByRole('heading', { name: 'Choose Your First Character' })).not.toBeInTheDocument()
    })
  })

  it('starts combat for an active team and surfaces socket command errors', async () => {
    const readyPlayer = guestAuthResponse({ activeTeamId: 'team-1', ownedCharacterKeys: ['hero'] })
    const activeTeam = {
      ...emptyTeam(),
      characterKeys: ['hero'],
      slots: [
        { position: 1, characterKey: 'hero', weaponItemId: null, armorItemId: null, accessoryItemId: null },
        { position: 2, characterKey: null, weaponItemId: null, armorItemId: null, accessoryItemId: null },
        { position: 3, characterKey: null, weaponItemId: null, armorItemId: null, accessoryItemId: null },
      ],
    }

    mocks.createGuestPlayer.mockResolvedValue(readyPlayer)
    mocks.getPlayer.mockResolvedValue(readyPlayer.player)
    mocks.getPlayerTeams.mockResolvedValue([activeTeam])
    mocks.getPlayerInventory.mockResolvedValue([])

    const user = userEvent.setup()
    render(<CombatScreen />)

    await user.type(screen.getByLabelText('Player name'), 'Scout')
    await user.click(screen.getByRole('button', { name: 'Create Guest' }))

    expect(await screen.findByRole('heading', { name: 'Scout' })).toBeInTheDocument()

    act(() => {
      mocks.lastSocketOptions?.onMessage({
        type: 'PLAYER_STATE_SYNC',
        playerId: 'player-1',
        snapshot: playerSnapshot(),
      })
    })

    await user.click(screen.getByRole('button', { name: 'Start Combat' }))

    await waitFor(() => {
      expect(mocks.sendStartCombat).toHaveBeenCalledTimes(1)
      expect(mocks.socket.send).toHaveBeenCalledWith(JSON.stringify({ type: 'START_COMBAT' }))
    })

    act(() => {
      mocks.lastSocketOptions?.onMessage({
        type: 'COMMAND_ERROR',
        playerId: 'player-1',
        commandType: 'START_COMBAT',
        message: 'Combat already running.',
        serverTime: '2099-01-01T00:00:00Z',
      })
    })

    expect(await screen.findByText('Combat already running.')).toBeInTheDocument()
  })

  it('surfaces invalid socket payloads instead of dropping them silently', async () => {
    const readyPlayer = guestAuthResponse({ ownedCharacterKeys: ['hero'] })

    mocks.createGuestPlayer.mockResolvedValue(readyPlayer)
    mocks.getPlayer.mockResolvedValue(readyPlayer.player)
    mocks.getPlayerTeams.mockResolvedValue([emptyTeam()])
    mocks.getPlayerInventory.mockResolvedValue([])

    const user = userEvent.setup()
    render(<CombatScreen />)

    await user.type(screen.getByLabelText('Player name'), 'Scout')
    await user.click(screen.getByRole('button', { name: 'Create Guest' }))
    expect(await screen.findByRole('heading', { name: 'Scout' })).toBeInTheDocument()

    act(() => {
      mocks.lastSocketOptions?.onInvalidMessage?.({
        code: 'INVALID_MESSAGE_SHAPE',
        message: 'COMBAT_STATE_SYNC payload is missing required combat snapshot fields.',
      })
    })

    expect(await screen.findByText('Received an unsupported realtime update from the server. Refresh if the UI looks stale.')).toBeInTheDocument()
  })

  it('renders team hp and enemy retaliation from combat sync', async () => {
    const readyPlayer = guestAuthResponse({ activeTeamId: 'team-1', ownedCharacterKeys: ['hero'] })
    const activeTeam = {
      ...emptyTeam(),
      characterKeys: ['hero'],
      slots: [
        { position: 1, characterKey: 'hero', weaponItemId: null, armorItemId: null, accessoryItemId: null },
        { position: 2, characterKey: null, weaponItemId: null, armorItemId: null, accessoryItemId: null },
        { position: 3, characterKey: null, weaponItemId: null, armorItemId: null, accessoryItemId: null },
      ],
    }

    mocks.createGuestPlayer.mockResolvedValue(readyPlayer)
    mocks.getPlayer.mockResolvedValue(readyPlayer.player)
    mocks.getPlayerTeams.mockResolvedValue([activeTeam])
    mocks.getPlayerInventory.mockResolvedValue([])

    const user = userEvent.setup()
    render(<CombatScreen />)

    await user.type(screen.getByLabelText('Player name'), 'Scout')
    await user.click(screen.getByRole('button', { name: 'Create Guest' }))
    expect(await screen.findByRole('heading', { name: 'Scout' })).toBeInTheDocument()

    act(() => {
      mocks.lastSocketOptions?.onMessage({
        type: 'COMBAT_STATE_SYNC',
        playerId: 'player-1',
        snapshot: {
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
          members: [
            {
              characterKey: 'hero',
              attack: 10,
              hit: 0.55,
              currentHp: 23,
              maxHp: 24,
              alive: true,
            },
          ],
        },
        serverTime: '2099-01-01T00:00:00Z',
      })
    })

    expect(await screen.findByText('23 / 24')).toBeInTheDocument()
    expect(screen.getByText('1.0')).toBeInTheDocument()
    expect(screen.getByText(/retaliates for 1.0/i)).toBeInTheDocument()
  })

  it('renders the revive countdown when the team is down', async () => {
    const readyPlayer = guestAuthResponse({ activeTeamId: 'team-1', ownedCharacterKeys: ['hero'] })

    mocks.createGuestPlayer.mockResolvedValue(readyPlayer)
    mocks.getPlayer.mockResolvedValue(readyPlayer.player)
    mocks.getPlayerTeams.mockResolvedValue([emptyTeam()])
    mocks.getPlayerInventory.mockResolvedValue([])

    const user = userEvent.setup()
    render(<CombatScreen />)

    await user.type(screen.getByLabelText('Player name'), 'Scout')
    await user.click(screen.getByRole('button', { name: 'Create Guest' }))
    expect(await screen.findByRole('heading', { name: 'Scout' })).toBeInTheDocument()

    act(() => {
      mocks.lastSocketOptions?.onMessage({
        type: 'COMBAT_STATE_SYNC',
        playerId: 'player-1',
        snapshot: {
          playerId: 'player-1',
          status: 'DOWN',
          zoneId: 'starter-plains',
          activeTeamId: 'team-1',
          enemyName: 'Training Slime',
          enemyAttack: 1,
          enemyHp: 19,
          enemyMaxHp: 20,
          teamDps: 0,
          pendingReviveMillis: 12000,
          members: [
            {
              characterKey: 'hero',
              attack: 10,
              hit: 0.55,
              currentHp: 0,
              maxHp: 24,
              alive: false,
            },
          ],
        },
        serverTime: '2099-01-01T00:00:00Z',
      })
    })

    expect(await screen.findByText(/DOWN · Revive in 18s/i)).toBeInTheDocument()
    expect(screen.getByText(/revives in 18s/i)).toBeInTheDocument()
    await act(async () => {
      await new Promise((resolve) => window.setTimeout(resolve, 1100))
    })
    expect(await screen.findByText(/DOWN · Revive in 17s/i)).toBeInTheDocument()
  })

  it('sends a stop combat command from the combat view', async () => {
    const readyPlayer = guestAuthResponse({ activeTeamId: 'team-1', ownedCharacterKeys: ['hero'] })

    mocks.createGuestPlayer.mockResolvedValue(readyPlayer)
    mocks.getPlayer.mockResolvedValue(readyPlayer.player)
    mocks.getPlayerTeams.mockResolvedValue([emptyTeam()])
    mocks.getPlayerInventory.mockResolvedValue([])

    const user = userEvent.setup()
    render(<CombatScreen />)

    await user.type(screen.getByLabelText('Player name'), 'Scout')
    await user.click(screen.getByRole('button', { name: 'Create Guest' }))
    expect(await screen.findByRole('heading', { name: 'Scout' })).toBeInTheDocument()

    act(() => {
      mocks.lastSocketOptions?.onMessage({
        type: 'COMBAT_STATE_SYNC',
        playerId: 'player-1',
        snapshot: {
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
          members: [],
        },
        serverTime: '2099-01-01T00:00:00Z',
      })
    })

    await user.click(screen.getByRole('button', { name: 'Stop Combat' }))

    await waitFor(() => {
      expect(mocks.sendStopCombat).toHaveBeenCalledTimes(1)
      expect(mocks.socket.send).toHaveBeenCalledWith(JSON.stringify({ type: 'STOP_COMBAT' }))
    })
  })

  it('supports triggering a ten-pull from the gacha view', async () => {
    const readyPlayer = guestAuthResponse({ ownedCharacterKeys: ['hero'] })

    mocks.createGuestPlayer.mockResolvedValue(readyPlayer)
    mocks.getPlayer.mockResolvedValue(readyPlayer.player)
    mocks.getPlayerTeams.mockResolvedValue([emptyTeam()])
    mocks.getPlayerInventory.mockResolvedValue([])
    mocks.pullCharacter.mockResolvedValue({
      player: { ...readyPlayer.player, gold: 0, essence: 15 },
      count: 10,
      pulls: Array.from({ length: 10 }, (_, index) => ({
        pulledCharacterKey: `hero-${index}`,
        grantedCharacterKey: `hero-${index}`,
        essenceGranted: 0,
      })),
      totalEssenceGranted: 0,
    })

    const user = userEvent.setup()
    render(<CombatScreen />)

    await user.type(screen.getByLabelText('Player name'), 'Scout')
    await user.click(screen.getByRole('button', { name: 'Create Guest' }))
    expect(await screen.findByRole('heading', { name: 'Scout' })).toBeInTheDocument()
    await user.click(await screen.findByText('Gacha'))
    await user.click(await screen.findByRole('button', { name: 'Pull x10' }))

    await waitFor(() => {
      expect(mocks.pullCharacter).toHaveBeenCalledWith('player-1', 10)
      expect(screen.getByText('Count: 10')).toBeInTheDocument()
    })
  })
})
