import { startTransition, useEffect, useMemo, useRef, useState } from 'react'
import { currentSessionExpiresAt, currentSessionPlayerId, setSession } from '../../../core/api/httpClient'
import {
  claimStarterCharacter,
  createGuestPlayer,
  getCharacterTemplates,
  getPlayer,
  getPlayerInventory,
  getPlayerTeams,
  getStarterCharacterTemplates,
  loginPlayer,
  logoutPlayer,
  pullCharacter,
  signUpPlayer,
} from '../../../core/api/playerApi'
import { createPlayerSocket, sendStartCombat, sendStopCombat } from '../../../core/api/wsClient'
import { activateTeam, assignCharacterToTeam, equipTeamItem, unequipTeamItem } from '../../../core/api/teamApi'
import type { SocketMessageParseError } from '../../../core/api/socketMessages'
import type {
  CharacterPull,
  CharacterTemplate,
  CombatSnapshot,
  EquipmentSlot,
  InventoryItemSnapshot,
  OfflineProgressionMessage,
  OwnedCharacterSnapshot,
  Player,
  PlayerSocketMessage,
  Team,
  ZoneLevelUpMessage,
  ZoneProgressSnapshot,
} from '../../../core/types/api'
import { applyCombatStateSnapshot, applyPlayerStateSnapshot } from './socketSync'

export type SocketStatus = 'disconnected' | 'connecting' | 'connected' | 'error'

export interface HudNotification {
  id: string
  title: string
  detail: string
  tone: 'neutral' | 'success' | 'accent'
  at: string
  mergeGroup?: string
  rangeStart?: number
  rangeEnd?: number
}

export interface ActivityEntry {
  id: string
  label: string
}

export interface PullResult {
  count: number
  pulls: CharacterPull[]
  totalEssenceGranted: number
}

export function useGameClient() {
  const [player, setPlayer] = useState<Player | null>(null)
  const [teams, setTeams] = useState<Team[]>([])
  const [inventory, setInventory] = useState<InventoryItemSnapshot[]>([])
  const [ownedCharacters, setOwnedCharacters] = useState<OwnedCharacterSnapshot[]>([])
  const [zoneProgress, setZoneProgress] = useState<ZoneProgressSnapshot[]>([])
  const [templates, setTemplates] = useState<CharacterTemplate[]>([])
  const [starterTemplates, setStarterTemplates] = useState<CharacterTemplate[]>([])
  const [combat, setCombat] = useState<CombatSnapshot | null>(null)
  const [socketStatus, setSocketStatus] = useState<SocketStatus>('disconnected')
  const [notifications, setNotifications] = useState<HudNotification[]>([])
  const [activity, setActivity] = useState<ActivityEntry[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [sessionExpiresAt, setSessionExpiresAt] = useState<string | null>(null)
  const [latestPullResult, setLatestPullResult] = useState<PullResult | null>(null)
  const socketRef = useRef<WebSocket | null>(null)
  const syncStateRef = useRef({
    player: null as Player | null,
    inventory: [] as InventoryItemSnapshot[],
    ownedCharacters: [] as OwnedCharacterSnapshot[],
    zoneProgress: [] as ZoneProgressSnapshot[],
  })
  const activitySequenceRef = useRef(0)
  const restoreAttemptedRef = useRef(false)

  syncStateRef.current = {
    player,
    inventory,
    ownedCharacters,
    zoneProgress,
  }

  useEffect(() => {
    void Promise.all([getCharacterTemplates(), getStarterCharacterTemplates()])
      .then(([allTemplates, starterChoices]) => {
        setTemplates(allTemplates)
        setStarterTemplates(starterChoices)
      })
      .catch(() => {
        setTemplates([])
        setStarterTemplates([])
      })
  }, [])

  useEffect(() => {
    if (restoreAttemptedRef.current) {
      return
    }
    restoreAttemptedRef.current = true

    const playerId = currentSessionPlayerId()
    if (!playerId) {
      return
    }

    setLoading(true)
    setSessionExpiresAt(currentSessionExpiresAt())
    void refreshPlayerState(playerId)
      .catch(() => {
        setSession(null)
        setSessionExpiresAt(null)
        setPlayer(null)
        setTeams([])
        setInventory([])
        setOwnedCharacters([])
        setZoneProgress([])
        setCombat(null)
        setNotifications([])
        setLatestPullResult(null)
        setError('Saved session could not be restored. Please sign in again.')
      })
      .finally(() => {
        setLoading(false)
      })
  }, [])

  useEffect(() => {
    if (!player) {
      socketRef.current?.close()
      socketRef.current = null
      setSocketStatus('disconnected')
      return
    }

    setSocketStatus('connecting')
    const socket = createPlayerSocket(player.id, {
      onOpen: () => setSocketStatus('connected'),
      onClose: () => setSocketStatus('disconnected'),
      onError: () => setSocketStatus('error'),
      onMessage: (message) => {
        startTransition(() => {
          applySocketMessage(message)
        })
      },
      onInvalidMessage: (error) => {
        startTransition(() => {
          handleInvalidSocketMessage(error)
        })
      },
    })
    socketRef.current = socket

    return () => {
      socket.close()
      if (socketRef.current === socket) {
        socketRef.current = null
      }
    }
  }, [player?.id])

  function applySocketMessage(message: PlayerSocketMessage) {
    const activityEntry = describeSocketEvent(message)
    if (activityEntry) {
      appendActivity(activityEntry)
    }
    if (message.type === 'PLAYER_STATE_SYNC') {
      const nextState = applyPlayerStateSnapshot(
        syncStateRef.current.player,
        syncStateRef.current.inventory,
        syncStateRef.current.ownedCharacters,
        syncStateRef.current.zoneProgress,
        message.snapshot,
      )
      setInventory(nextState.inventory)
      setOwnedCharacters(nextState.ownedCharacters)
      setZoneProgress(nextState.zoneProgress)
      setPlayer(nextState.player)
      return
    }
    if (message.type === 'COMBAT_STATE_SYNC') {
      setCombat((current) => applyCombatStateSnapshot(current, message.snapshot))
      return
    }
    if (message.type === 'ZONE_LEVEL_UP') {
      pushNotification(zoneLevelUpNotification(message))
      return
    }
    if (message.type === 'COMMAND_ERROR') {
      setError(message.message)
      return
    }
    pushNotification(offlineProgressionNotification(message))
  }

  function handleInvalidSocketMessage(error: SocketMessageParseError) {
    appendActivity(`Ignored invalid socket payload: ${error.message}`)
    setError('Received an unsupported realtime update from the server. Refresh if the UI looks stale.')
  }

  function pushNotification(notification: HudNotification) {
    setNotifications((current) => {
      if (notification.mergeGroup) {
        const existing = current.find((entry) => entry.mergeGroup === notification.mergeGroup)
        if (existing) {
          const merged = mergeNotification(existing, notification)
          return [merged, ...current.filter((entry) => entry.id !== existing.id)].slice(0, 10)
        }
      }

      return [notification, ...current].slice(0, 10)
    })
  }

  function appendActivity(entry: string) {
    const nextId = `activity-${activitySequenceRef.current}`
    activitySequenceRef.current += 1
    setActivity((current) => [{ id: nextId, label: `${timestampLabel()} ${entry}` }, ...current].slice(0, 12))
  }

  async function refreshPlayerState(playerId: string) {
    const [reloadedPlayer, playerTeams, playerInventory] = await Promise.all([
      getPlayer(playerId),
      getPlayerTeams(playerId),
      getPlayerInventory(playerId),
    ])
    setPlayer(reloadedPlayer)
    setTeams(playerTeams)
    setInventory(playerInventory)
  }

  const actions = useMemo(
    () => ({
      async signUp(name: string, email: string | null, password: string) {
        setLoading(true)
        setError(null)
        try {
          const response = await signUpPlayer(name, email, password)
          setSession(response, response.player.id)
          setSessionExpiresAt(response.sessionExpiresAt)
          setPlayer(response.player)
          setCombat(null)
          setNotifications([])
          await refreshPlayerState(response.player.id)
          appendActivity(`Signed up as ${response.player.name}`)
        } catch (caught) {
          setError(extractMessage(caught))
        } finally {
          setLoading(false)
        }
      },
      async login(identity: string, password: string) {
        setLoading(true)
        setError(null)
        try {
          const trimmedIdentity = identity.trim()
          const response =
            trimmedIdentity.includes('@')
              ? await loginPlayer({ email: trimmedIdentity, password })
              : await loginPlayer({ name: trimmedIdentity, password })
          setSession(response, response.player.id)
          setSessionExpiresAt(response.sessionExpiresAt)
          setPlayer(response.player)
          setCombat(null)
          setNotifications([])
          await refreshPlayerState(response.player.id)
          appendActivity(`Logged in as ${response.player.name}`)
        } catch (caught) {
          setError(extractMessage(caught))
        } finally {
          setLoading(false)
        }
      },
      async createPlayer(name: string) {
        setLoading(true)
        setError(null)
        try {
          const response = await createGuestPlayer(name)
          setSession(response, response.player.id)
          setSessionExpiresAt(response.sessionExpiresAt)
          setPlayer(response.player)
          setCombat(null)
          setNotifications([])
          await refreshPlayerState(response.player.id)
          appendActivity(`Signed in as ${response.player.name}`)
        } catch (caught) {
          setError(extractMessage(caught))
        } finally {
          setLoading(false)
        }
      },
      async claimStarter(characterKey: string) {
        if (!player) {
          return
        }
        setLoading(true)
        setError(null)
        try {
          await claimStarterCharacter(player.id, characterKey)
          await refreshPlayerState(player.id)
          appendActivity(`Claimed starter ${characterKey}`)
        } catch (caught) {
          setError(extractMessage(caught))
        } finally {
          setLoading(false)
        }
      },
      async pullCharacter(count = 1) {
        if (!player) {
          return
        }
        setLoading(true)
        setError(null)
        try {
          const result = await pullCharacter(player.id, count)
          setPlayer(result.player)
          setLatestPullResult({
            count: result.count,
            pulls: result.pulls,
            totalEssenceGranted: result.totalEssenceGranted,
          })
          await refreshPlayerState(player.id)
          const unlockedCount = result.pulls.filter((pull) => pull.grantedCharacterKey != null).length
          const duplicateCount = result.pulls.length - unlockedCount
          const summary =
            result.count === 1
              ? result.pulls[0]?.grantedCharacterKey
                ? `Pulled ${result.pulls[0].grantedCharacterKey}`
                : `Pulled duplicate ${result.pulls[0]?.pulledCharacterKey} for +${result.pulls[0]?.essenceGranted ?? 0} essence`
              : `Pulled ${result.count} characters: ${unlockedCount} unlocks, ${duplicateCount} duplicates, +${result.totalEssenceGranted} essence`
          appendActivity(summary)
        } catch (caught) {
          setError(extractMessage(caught))
        } finally {
          setLoading(false)
        }
      },
      async refreshPlayer() {
        if (!player) {
          return
        }
        setLoading(true)
        setError(null)
        try {
          await refreshPlayerState(player.id)
          setSessionExpiresAt(currentSessionExpiresAt())
          appendActivity(`Refreshed ${player.name}`)
        } catch (caught) {
          setError(extractMessage(caught))
        } finally {
          setLoading(false)
        }
      },
      async assignCharacter(teamId: string, position: number, characterKey: string) {
        setLoading(true)
        setError(null)
        try {
          const updatedTeam = await assignCharacterToTeam(teamId, position, characterKey)
          setTeams((current) => current.map((team) => (team.id === updatedTeam.id ? updatedTeam : team)))
          if (player) {
            await refreshPlayerState(player.id)
          }
          appendActivity(`Assigned ${characterKey} to slot ${position}`)
        } catch (caught) {
          setError(extractMessage(caught))
        } finally {
          setLoading(false)
        }
      },
      async activateTeam(teamId: string) {
        if (!player) {
          return
        }
        setLoading(true)
        setError(null)
        try {
          const updatedTeam = await activateTeam(teamId)
          setTeams((current) => current.map((team) => (team.id === updatedTeam.id ? updatedTeam : team)))
          setPlayer((current) => (current == null ? current : { ...current, activeTeamId: teamId }))
          await refreshPlayerState(player.id)
          appendActivity(`Activated team ${teamId.slice(0, 8)}`)
        } catch (caught) {
          setError(extractMessage(caught))
        } finally {
          setLoading(false)
        }
      },
      async equipItem(teamId: string, position: number, inventoryItemId: string, slot: EquipmentSlot) {
        if (!player) {
          return
        }
        setLoading(true)
        setError(null)
        try {
          await equipTeamItem(teamId, position, inventoryItemId, slot)
          await refreshPlayerState(player.id)
          appendActivity(`Equipped ${slot.toLowerCase()} on slot ${position}`)
        } catch (caught) {
          setError(extractMessage(caught))
        } finally {
          setLoading(false)
        }
      },
      async unequipItem(teamId: string, position: number, slot: EquipmentSlot) {
        if (!player) {
          return
        }
        setLoading(true)
        setError(null)
        try {
          await unequipTeamItem(teamId, position, slot)
          await refreshPlayerState(player.id)
          appendActivity(`Unequipped ${slot.toLowerCase()} from slot ${position}`)
        } catch (caught) {
          setError(extractMessage(caught))
        } finally {
          setLoading(false)
        }
      },
      async startCombat() {
        if (!player) {
          return
        }
        const activeTeam = teams.find((team) => team.id === player.activeTeamId)
        if (!activeTeam || activeTeam.slots.every((slot) => slot.characterKey == null)) {
          setError('Activate a team with at least one character before starting combat.')
          return
        }
        const socket = socketRef.current
        if (socketStatus !== 'connected' || !socket || socket.readyState !== WebSocket.OPEN) {
          setError('Connect to the game server before starting combat.')
          return
        }
        setError(null)
        sendStartCombat(socket)
        appendActivity(`Sent combat start for ${player.name}`)
      },
      async stopCombat() {
        if (!player) {
          return
        }
        const socket = socketRef.current
        if (socketStatus !== 'connected' || !socket || socket.readyState !== WebSocket.OPEN) {
          setError('Connect to the game server before stopping combat.')
          return
        }
        setError(null)
        sendStopCombat(socket)
        appendActivity(`Sent combat stop for ${player.name}`)
      },
      dismissNotification(id: string) {
        setNotifications((current) => current.filter((notification) => notification.id !== id))
      },
      clearError() {
        setError(null)
      },
      async logout() {
        setLoading(true)
        setError(null)
        try {
          await logoutPlayer()
        } catch (caught) {
          const message = extractMessage(caught)
          if (message !== 'HTTP 401' && message !== 'HTTP 403' && message !== 'Session is no longer active.') {
            setError(message)
            setLoading(false)
            return
          }
        }

        socketRef.current?.close()
        socketRef.current = null
        setSession(null)
        setPlayer(null)
        setTeams([])
        setInventory([])
        setOwnedCharacters([])
        setZoneProgress([])
        setCombat(null)
        setNotifications([])
        setActivity([])
        setSessionExpiresAt(null)
        setLatestPullResult(null)
        setSocketStatus('disconnected')
        setLoading(false)
      },
    }),
    [player, socketStatus, teams],
  )

  return {
    player,
    teams,
    inventory,
    ownedCharacters,
    zoneProgress,
    templates,
    starterTemplates,
    combat,
    socketStatus,
    notifications,
    activity,
    sessionExpiresAt,
    latestPullResult,
    loading,
    error,
    actions,
  }
}

function describeSocketEvent(message: PlayerSocketMessage): string | null {
  switch (message.type) {
    case 'PLAYER_STATE_SYNC':
      return null
    case 'COMBAT_STATE_SYNC':
      return null
    case 'ZONE_LEVEL_UP':
      return `Zone level ${message.level} reached in ${message.zoneId}`
    case 'OFFLINE_PROGRESSION':
      return `Offline gains applied for ${message.zoneId}`
    case 'COMMAND_ERROR':
      return `${message.commandType} failed: ${message.message}`
  }
}

function zoneLevelUpNotification(message: ZoneLevelUpMessage): HudNotification {
  return {
    id: `zone-${message.zoneId}-${message.level}-${message.serverTime ?? timestampLabel()}`,
    title: 'Zone level up',
    detail: `${message.zoneId} reached level ${message.level}.`,
    tone: 'success',
    at: message.serverTime ?? new Date().toISOString(),
    mergeGroup: `zone-level-${message.zoneId}`,
    rangeStart: message.level,
    rangeEnd: message.level,
  }
}

function offlineProgressionNotification(message: OfflineProgressionMessage): HudNotification {
  const rewards =
    message.rewards.length === 0
      ? 'No notable drops'
      : message.rewards.map((reward) => `${reward.itemName} x${reward.count}`).join(', ')
  return {
    id: `offline-${message.playerId}-${message.serverTime ?? timestampLabel()}`,
    title: 'Offline progression',
    detail: `${formatDuration(message.offlineDurationMillis)} away · ${message.kills} kills · +${message.experienceGained} EXP · +${message.goldGained} gold · ${rewards}`,
    tone: 'accent',
    at: message.serverTime ?? new Date().toISOString(),
  }
}

function formatDuration(durationMillis: number) {
  const totalSeconds = Math.floor(durationMillis / 1000)
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60
  if (minutes === 0) {
    return `${seconds}s`
  }
  return `${minutes}m ${seconds}s`
}

function timestampLabel() {
  return new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
}

function extractMessage(caught: unknown) {
  if (caught instanceof Error) {
    return caught.message
  }
  return 'Unexpected frontend error.'
}

function mergeNotification(current: HudNotification, incoming: HudNotification): HudNotification {
  if (current.mergeGroup == null || incoming.mergeGroup == null || current.mergeGroup !== incoming.mergeGroup) {
    return incoming
  }

  const firstReceivedLevel = Math.min(current.rangeStart ?? incoming.rangeStart ?? 0, incoming.rangeStart ?? current.rangeStart ?? 0)
  const end = Math.max(current.rangeEnd ?? incoming.rangeEnd ?? 0, incoming.rangeEnd ?? current.rangeEnd ?? 0)

  if (incoming.title === 'Zone level up') {
    const zoneId = incoming.mergeGroup.replace('zone-level-', '')
    return {
      ...incoming,
      detail: `${zoneId} advanced from level ${firstReceivedLevel - 1} to ${end}.`,
      rangeStart: firstReceivedLevel,
      rangeEnd: end,
    }
  }

  return incoming
}
