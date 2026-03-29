import { startTransition, useEffect, useMemo, useRef, useState } from 'react'
import { currentSessionExpiresAt, setSession } from '../../../core/api/httpClient'
import {
  claimStarterCharacter,
  createGuestPlayer,
  getCharacterTemplates,
  getPlayer,
  getPlayerInventory,
  getPlayerTeams,
  getStarterCharacterTemplates,
  loginPlayer,
  pullCharacter,
  signUpPlayer,
} from '../../../core/api/playerApi'
import { sendStartCombat, createPlayerSocket } from '../../../core/api/wsClient'
import { activateTeam, assignCharacterToTeam, equipTeamItem, unequipTeamItem } from '../../../core/api/teamApi'
import type {
  CharacterTemplate,
  CombatSnapshot,
  EquipmentSlot,
  InventoryItemSnapshot,
  OfflineProgressionMessage,
  OwnedCharacterSnapshot,
  Player,
  PlayerStateSnapshot,
  PlayerSocketMessage,
  Stat,
  Team,
  ZoneLevelUpMessage,
  ZoneProgressSnapshot,
} from '../../../core/types/api'

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
  pulledCharacterKey: string
  grantedCharacterKey: string | null
  essenceGranted: number
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
  const activitySequenceRef = useRef(0)

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
      setInventory((current) => (inventoryEquals(current, message.snapshot.inventory) ? current : message.snapshot.inventory))
      setOwnedCharacters((current) =>
        ownedCharactersEquals(current, message.snapshot.ownedCharacters) ? current : message.snapshot.ownedCharacters,
      )
      setZoneProgress((current) => (zoneProgressEquals(current, message.snapshot.zoneProgress) ? current : message.snapshot.zoneProgress))
      setPlayer((current) =>
        current == null
          ? null
          : updatePlayerFromSnapshot(current, message.snapshot),
      )
      return
    }
    if (message.type === 'COMBAT_STATE_SYNC') {
      setCombat((current) => (combatEquals(current, message.snapshot) ? current : message.snapshot))
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
          setSession(response)
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
          setSession(response)
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
          setSession(response)
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
      async pullCharacter() {
        if (!player) {
          return
        }
        setLoading(true)
        setError(null)
        try {
          const result = await pullCharacter(player.id)
          setPlayer(result.player)
          setLatestPullResult({
            pulledCharacterKey: result.pulledCharacterKey,
            grantedCharacterKey: result.grantedCharacterKey,
            essenceGranted: result.essenceGranted,
          })
          await refreshPlayerState(player.id)
          appendActivity(
            result.grantedCharacterKey
              ? `Pulled ${result.grantedCharacterKey}`
              : `Pulled duplicate ${result.pulledCharacterKey} for +${result.essenceGranted} essence`,
          )
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
      dismissNotification(id: string) {
        setNotifications((current) => current.filter((notification) => notification.id !== id))
      },
      clearError() {
        setError(null)
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

function updatePlayerFromSnapshot(player: Player, snapshot: PlayerStateSnapshot) {
  const nextOwnedCharacterKeys = snapshot.ownedCharacters.map((character) => character.key)
  if (
    player.id === snapshot.playerId &&
    player.name === snapshot.playerName &&
    player.experience === snapshot.playerExperience &&
    player.level === snapshot.playerLevel &&
    player.gold === snapshot.playerGold &&
    player.essence === snapshot.playerEssence &&
    stringArrayEquals(player.ownedCharacterKeys, nextOwnedCharacterKeys)
  ) {
    return player
  }

  return {
    ...player,
    id: snapshot.playerId,
    name: snapshot.playerName,
    ownedCharacterKeys: nextOwnedCharacterKeys,
    experience: snapshot.playerExperience,
    level: snapshot.playerLevel,
    gold: snapshot.playerGold,
    essence: snapshot.playerEssence,
  }
}

function stringArrayEquals(left: string[], right: string[]) {
  return left.length === right.length && left.every((value, index) => value === right[index])
}

function ownedCharactersEquals(left: OwnedCharacterSnapshot[], right: OwnedCharacterSnapshot[]) {
  return (
    left.length === right.length &&
    left.every(
      (character, index) =>
        character.key === right[index]?.key &&
        character.name === right[index]?.name &&
        character.level === right[index]?.level,
    )
  )
}

function zoneProgressEquals(left: ZoneProgressSnapshot[], right: ZoneProgressSnapshot[]) {
  return (
    left.length === right.length &&
    left.every(
      (zone, index) =>
        zone.zoneId === right[index]?.zoneId &&
        zone.killCount === right[index]?.killCount &&
        zone.level === right[index]?.level,
    )
  )
}

function inventoryEquals(left: InventoryItemSnapshot[], right: InventoryItemSnapshot[]) {
  return (
    left.length === right.length &&
    left.every((item, index) => {
      const other = right[index]
      return (
        item.id === other?.id &&
        item.itemName === other?.itemName &&
        item.itemDisplayName === other?.itemDisplayName &&
        item.itemType === other?.itemType &&
        statEquals(item.itemBaseStat, other?.itemBaseStat) &&
        stringArrayEquals(item.itemSubStatPool, other?.itemSubStatPool ?? []) &&
        statsEquals(item.subStats, other?.subStats ?? []) &&
        item.rarity === other?.rarity &&
        item.upgrade === other?.upgrade &&
        item.equippedTeamId === other?.equippedTeamId &&
        item.equippedPosition === other?.equippedPosition
      )
    })
  )
}

function combatEquals(left: CombatSnapshot | null, right: CombatSnapshot | null) {
  if (left === right) {
    return true
  }
  if (left == null || right == null) {
    return false
  }

  return (
    left.playerId === right.playerId &&
    left.status === right.status &&
    left.zoneId === right.zoneId &&
    left.activeTeamId === right.activeTeamId &&
    left.enemyName === right.enemyName &&
    left.enemyHp === right.enemyHp &&
    left.enemyMaxHp === right.enemyMaxHp &&
    left.teamDps === right.teamDps &&
    combatMembersEquals(left.members, right.members)
  )
}

function combatMembersEquals(left: CombatSnapshot['members'], right: CombatSnapshot['members']) {
  return (
    left.length === right.length &&
    left.every(
      (member, index) =>
        member.characterKey === right[index]?.characterKey &&
        member.attack === right[index]?.attack &&
        member.hit === right[index]?.hit &&
        member.currentHp === right[index]?.currentHp &&
        member.maxHp === right[index]?.maxHp &&
        member.alive === right[index]?.alive,
    )
  )
}

function statsEquals(left: Stat[], right: Stat[]) {
  return left.length === right.length && left.every((stat, index) => statEquals(stat, right[index]))
}

function statEquals(left: Stat, right: Stat | undefined) {
  return left.type === right?.type && left.value === right?.value
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
