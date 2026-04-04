import { useRef, useState } from 'react'
import type { SocketMessageParseError } from '../../../core/api/socketMessages'
import type {
  CharacterTemplate,
  InventoryItemSnapshot,
  OfflineProgressionMessage,
  Player,
  PlayerSocketMessage,
  Team,
  ZoneLevelUpMessage,
} from '../../../core/types/api'
import type { ActivityEntry, HudNotification, PullResult } from './gameClientTypes'
import { applyCombatStateSnapshot, applyPlayerStateSnapshot } from './socketSync'
import type {
  ClientCharacterTemplate,
  ClientCombat,
  ClientInventoryItem,
  ClientOwnedCharacter,
  ClientPlayer,
  ClientTeam,
  ClientZoneProgress,
} from '../model/clientModels'
import { mapCharacterTemplates, mapInventory, mapPlayer, mapTeams } from '../model/clientModels'

export function useGameClientState() {
  const [player, setPlayer] = useState<ClientPlayer | null>(null)
  const [teams, setTeams] = useState<ClientTeam[]>([])
  const [inventory, setInventory] = useState<ClientInventoryItem[]>([])
  const [ownedCharacters, setOwnedCharacters] = useState<ClientOwnedCharacter[]>([])
  const [zoneProgress, setZoneProgress] = useState<ClientZoneProgress[]>([])
  const [templates, setTemplates] = useState<ClientCharacterTemplate[]>([])
  const [starterTemplates, setStarterTemplates] = useState<ClientCharacterTemplate[]>([])
  const [combat, setCombat] = useState<ClientCombat | null>(null)
  const [notifications, setNotifications] = useState<HudNotification[]>([])
  const [activity, setActivity] = useState<ActivityEntry[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [sessionExpiresAt, setSessionExpiresAt] = useState<string | null>(null)
  const [latestPullResult, setLatestPullResult] = useState<PullResult | null>(null)
  const syncStateRef = useRef({
    player: null as ClientPlayer | null,
    inventory: [] as ClientInventoryItem[],
    ownedCharacters: [] as ClientOwnedCharacter[],
    zoneProgress: [] as ClientZoneProgress[],
  })
  const activitySequenceRef = useRef(0)

  syncStateRef.current = {
    player,
    inventory,
    ownedCharacters,
    zoneProgress,
  }

  function applyRefreshedPlayerState(reloadedPlayer: Player, playerTeams: Team[], playerInventory: InventoryItemSnapshot[]) {
    setPlayer(mapPlayer(reloadedPlayer))
    setTeams(mapTeams(playerTeams))
    setInventory(mapInventory(playerInventory))
  }

  function resetPlayerState() {
    setPlayer(null)
    setTeams([])
    setInventory([])
    setOwnedCharacters([])
    setZoneProgress([])
    setCombat(null)
    setNotifications([])
    setLatestPullResult(null)
  }

  function resetClientState() {
    resetPlayerState()
    setActivity([])
    setSessionExpiresAt(null)
    setError(null)
    setLoading(false)
  }

  function appendActivity(entry: string) {
    const nextId = `activity-${activitySequenceRef.current}`
    activitySequenceRef.current += 1
    setActivity((current) => [{ id: nextId, label: `${timestampLabel()} ${entry}` }, ...current].slice(0, 12))
  }

  function dismissNotification(id: string) {
    setNotifications((current) => current.filter((notification) => notification.id !== id))
  }

  function clearError() {
    setError(null)
  }

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

  return {
    player,
    teams,
    inventory,
    ownedCharacters,
    zoneProgress,
    templates,
    starterTemplates,
    combat,
    notifications,
    activity,
    loading,
    error,
    sessionExpiresAt,
    latestPullResult,
    setPlayer,
    setTeams,
    setInventory,
    setOwnedCharacters,
    setZoneProgress,
    setTemplates: (value: CharacterTemplate[]) => setTemplates(mapCharacterTemplates(value)),
    setStarterTemplates: (value: CharacterTemplate[]) => setStarterTemplates(mapCharacterTemplates(value)),
    setCombat,
    setLoading,
    setError,
    setSessionExpiresAt,
    setLatestPullResult,
    setNotifications,
    applyRefreshedPlayerState,
    resetPlayerState,
    resetClientState,
    appendActivity,
    dismissNotification,
    clearError,
    applySocketMessage,
    handleInvalidSocketMessage,
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
