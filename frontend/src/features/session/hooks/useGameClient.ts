import { useMemo } from 'react'
import { currentSessionExpiresAt, setSession } from '../../../core/api/httpClient'
import {
  claimStarterCharacter,
  createGuestPlayer,
  getPlayer,
  getPlayerInventory,
  getPlayerTeams,
  loginPlayer,
  logoutPlayer,
  pullCharacter,
  signUpPlayer,
} from '../../../core/api/playerApi'
import { sendStartCombat, sendStopCombat } from '../../../core/api/wsClient'
import { activateTeam, assignCharacterToTeam, equipTeamItem, unequipTeamItem } from '../../../core/api/teamApi'
import type { EquipmentSlot } from '../../../core/types/api'
import { useGameClientState } from './useGameClientState'
import { useGameSession } from './useGameSession'
import { usePlayerSocket } from './usePlayerSocket'

export { type ActivityEntry, type HudNotification, type PullResult, type SocketStatus } from './gameClientTypes'

export function useGameClient() {
  const state = useGameClientState()
  const socket = usePlayerSocket({
    playerId: state.player?.id ?? null,
    onMessage: state.applySocketMessage,
    onInvalidMessage: state.handleInvalidSocketMessage,
  })

  async function refreshPlayerState(playerId: string) {
    const [reloadedPlayer, playerTeams, playerInventory] = await Promise.all([
      getPlayer(playerId),
      getPlayerTeams(playerId),
      getPlayerInventory(playerId),
    ])
    state.applyRefreshedPlayerState(reloadedPlayer, playerTeams, playerInventory)
  }

  useGameSession({
    onTemplatesLoaded: (templates, starterTemplates) => {
      state.setTemplates(templates)
      state.setStarterTemplates(starterTemplates)
    },
    onTemplatesFailed: () => {
      state.setTemplates([])
      state.setStarterTemplates([])
    },
    onRestoreStarted: (sessionExpiresAt) => {
      state.setLoading(true)
      state.setSessionExpiresAt(sessionExpiresAt)
    },
    onRestoreFailed: () => {
      state.resetPlayerState()
      state.setSessionExpiresAt(null)
      state.setError('Saved session could not be restored. Please sign in again.')
    },
    onRestoreFinished: () => {
      state.setLoading(false)
    },
    restorePlayerState: refreshPlayerState,
  })

  const actions = useMemo(
    () => ({
      async signUp(name: string, email: string | null, password: string) {
        state.setLoading(true)
        state.setError(null)
        try {
          const response = await signUpPlayer(name, email, password)
          setSession(response, response.player.id)
          state.setSessionExpiresAt(response.sessionExpiresAt)
          state.setPlayer(response.player)
          state.setCombat(null)
          state.setNotifications([])
          await refreshPlayerState(response.player.id)
          state.appendActivity(`Signed up as ${response.player.name}`)
        } catch (caught) {
          state.setError(extractMessage(caught))
        } finally {
          state.setLoading(false)
        }
      },
      async login(identity: string, password: string) {
        state.setLoading(true)
        state.setError(null)
        try {
          const trimmedIdentity = identity.trim()
          const response =
            trimmedIdentity.includes('@')
              ? await loginPlayer({ email: trimmedIdentity, password })
              : await loginPlayer({ name: trimmedIdentity, password })
          setSession(response, response.player.id)
          state.setSessionExpiresAt(response.sessionExpiresAt)
          state.setPlayer(response.player)
          state.setCombat(null)
          state.setNotifications([])
          await refreshPlayerState(response.player.id)
          state.appendActivity(`Logged in as ${response.player.name}`)
        } catch (caught) {
          state.setError(extractMessage(caught))
        } finally {
          state.setLoading(false)
        }
      },
      async createPlayer(name: string) {
        state.setLoading(true)
        state.setError(null)
        try {
          const response = await createGuestPlayer(name)
          setSession(response, response.player.id)
          state.setSessionExpiresAt(response.sessionExpiresAt)
          state.setPlayer(response.player)
          state.setCombat(null)
          state.setNotifications([])
          await refreshPlayerState(response.player.id)
          state.appendActivity(`Signed in as ${response.player.name}`)
        } catch (caught) {
          state.setError(extractMessage(caught))
        } finally {
          state.setLoading(false)
        }
      },
      async claimStarter(characterKey: string) {
        if (!state.player) {
          return
        }
        state.setLoading(true)
        state.setError(null)
        try {
          await claimStarterCharacter(state.player.id, characterKey)
          await refreshPlayerState(state.player.id)
          state.appendActivity(`Claimed starter ${characterKey}`)
        } catch (caught) {
          state.setError(extractMessage(caught))
        } finally {
          state.setLoading(false)
        }
      },
      async pullCharacter(count = 1) {
        if (!state.player) {
          return
        }
        state.setLoading(true)
        state.setError(null)
        try {
          const result = await pullCharacter(state.player.id, count)
          state.setPlayer(result.player)
          state.setLatestPullResult({
            count: result.count,
            pulls: result.pulls,
            totalEssenceGranted: result.totalEssenceGranted,
          })
          await refreshPlayerState(state.player.id)
          const unlockedCount = result.pulls.filter((pull) => pull.grantedCharacterKey != null).length
          const duplicateCount = result.pulls.length - unlockedCount
          const summary =
            result.count === 1
              ? result.pulls[0]?.grantedCharacterKey
                ? `Pulled ${result.pulls[0].grantedCharacterKey}`
                : `Pulled duplicate ${result.pulls[0]?.pulledCharacterKey} for +${result.pulls[0]?.essenceGranted ?? 0} essence`
              : `Pulled ${result.count} characters: ${unlockedCount} unlocks, ${duplicateCount} duplicates, +${result.totalEssenceGranted} essence`
          state.appendActivity(summary)
        } catch (caught) {
          state.setError(extractMessage(caught))
        } finally {
          state.setLoading(false)
        }
      },
      async refreshPlayer() {
        if (!state.player) {
          return
        }
        state.setLoading(true)
        state.setError(null)
        try {
          await refreshPlayerState(state.player.id)
          state.setSessionExpiresAt(currentSessionExpiresAt())
          state.appendActivity(`Refreshed ${state.player.name}`)
        } catch (caught) {
          state.setError(extractMessage(caught))
        } finally {
          state.setLoading(false)
        }
      },
      async assignCharacter(teamId: string, position: number, characterKey: string) {
        state.setLoading(true)
        state.setError(null)
        try {
          const updatedTeam = await assignCharacterToTeam(teamId, position, characterKey)
          state.setTeams((current) => current.map((team) => (team.id === updatedTeam.id ? updatedTeam : team)))
          if (state.player) {
            await refreshPlayerState(state.player.id)
          }
          state.appendActivity(`Assigned ${characterKey} to slot ${position}`)
        } catch (caught) {
          state.setError(extractMessage(caught))
        } finally {
          state.setLoading(false)
        }
      },
      async activateTeam(teamId: string) {
        if (!state.player) {
          return
        }
        state.setLoading(true)
        state.setError(null)
        try {
          const updatedTeam = await activateTeam(teamId)
          state.setTeams((current) => current.map((team) => (team.id === updatedTeam.id ? updatedTeam : team)))
          state.setPlayer((current) => (current == null ? current : { ...current, activeTeamId: teamId }))
          await refreshPlayerState(state.player.id)
          state.appendActivity(`Activated team ${teamId.slice(0, 8)}`)
        } catch (caught) {
          state.setError(extractMessage(caught))
        } finally {
          state.setLoading(false)
        }
      },
      async equipItem(teamId: string, position: number, inventoryItemId: string, slotName: EquipmentSlot) {
        if (!state.player) {
          return
        }
        state.setLoading(true)
        state.setError(null)
        try {
          await equipTeamItem(teamId, position, inventoryItemId, slotName)
          await refreshPlayerState(state.player.id)
          state.appendActivity(`Equipped ${slotName.toLowerCase()} on slot ${position}`)
        } catch (caught) {
          state.setError(extractMessage(caught))
        } finally {
          state.setLoading(false)
        }
      },
      async unequipItem(teamId: string, position: number, slotName: EquipmentSlot) {
        if (!state.player) {
          return
        }
        state.setLoading(true)
        state.setError(null)
        try {
          await unequipTeamItem(teamId, position, slotName)
          await refreshPlayerState(state.player.id)
          state.appendActivity(`Unequipped ${slotName.toLowerCase()} from slot ${position}`)
        } catch (caught) {
          state.setError(extractMessage(caught))
        } finally {
          state.setLoading(false)
        }
      },
      async startCombat() {
        if (!state.player) {
          return
        }
        const activeTeam = state.teams.find((team) => team.id === state.player?.activeTeamId)
        if (!activeTeam || activeTeam.slots.every((slot) => slot.characterKey == null)) {
          state.setError('Activate a team with at least one character before starting combat.')
          return
        }
        const activeSocket = socket.socketRef.current
        if (socket.socketStatus !== 'connected' || !activeSocket || activeSocket.readyState !== WebSocket.OPEN) {
          state.setError('Connect to the game server before starting combat.')
          return
        }
        state.setError(null)
        sendStartCombat(activeSocket)
        state.appendActivity(`Sent combat start for ${state.player.name}`)
      },
      async stopCombat() {
        if (!state.player) {
          return
        }
        const activeSocket = socket.socketRef.current
        if (socket.socketStatus !== 'connected' || !activeSocket || activeSocket.readyState !== WebSocket.OPEN) {
          state.setError('Connect to the game server before stopping combat.')
          return
        }
        state.setError(null)
        sendStopCombat(activeSocket)
        state.appendActivity(`Sent combat stop for ${state.player.name}`)
      },
      dismissNotification: state.dismissNotification,
      clearError: state.clearError,
      async logout() {
        state.setLoading(true)
        state.setError(null)
        try {
          await logoutPlayer()
        } catch (caught) {
          const message = extractMessage(caught)
          if (message !== 'HTTP 401' && message !== 'HTTP 403' && message !== 'Session is no longer active.') {
            state.setError(message)
            state.setLoading(false)
            return
          }
        }

        socket.socketRef.current?.close()
        socket.socketRef.current = null
        setSession(null)
        state.resetClientState()
      },
    }),
    [socket.socketRef, socket.socketStatus, state],
  )

  return {
    player: state.player,
    teams: state.teams,
    inventory: state.inventory,
    ownedCharacters: state.ownedCharacters,
    zoneProgress: state.zoneProgress,
    templates: state.templates,
    starterTemplates: state.starterTemplates,
    combat: state.combat,
    socketStatus: socket.socketStatus,
    notifications: state.notifications,
    activity: state.activity,
    sessionExpiresAt: state.sessionExpiresAt,
    latestPullResult: state.latestPullResult,
    loading: state.loading,
    error: state.error,
    actions,
  }
}

function extractMessage(caught: unknown) {
  if (caught instanceof Error) {
    return caught.message
  }
  return 'Unexpected frontend error.'
}
