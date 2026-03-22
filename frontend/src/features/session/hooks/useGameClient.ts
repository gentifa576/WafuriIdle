import { startTransition, useEffect, useMemo, useState } from 'react'
import {
  createPlayer,
  getCharacterTemplates,
  getPlayer,
  getPlayerInventory,
  getPlayerTeams,
  startCombat,
} from '../../../core/api/playerApi'
import { activateTeam, assignCharacterToTeam } from '../../../core/api/teamApi'
import { createPlayerSocket } from '../../../core/api/wsClient'
import type {
  CharacterTemplate,
  CombatSnapshot,
  InventoryItemSnapshot,
  OwnedCharacterSnapshot,
  Player,
  PlayerSocketMessage,
  Team,
} from '../../../core/types/api'

export type SocketStatus = 'disconnected' | 'connecting' | 'connected' | 'error'

export function useGameClient() {
  const [player, setPlayer] = useState<Player | null>(null)
  const [teams, setTeams] = useState<Team[]>([])
  const [inventory, setInventory] = useState<InventoryItemSnapshot[]>([])
  const [ownedCharacters, setOwnedCharacters] = useState<OwnedCharacterSnapshot[]>([])
  const [templates, setTemplates] = useState<CharacterTemplate[]>([])
  const [combat, setCombat] = useState<CombatSnapshot | null>(null)
  const [socketStatus, setSocketStatus] = useState<SocketStatus>('disconnected')
  const [log, setLog] = useState<string[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    void getCharacterTemplates()
      .then(setTemplates)
      .catch(() => {
        setTemplates([])
      })
  }, [])

  useEffect(() => {
    if (!player) {
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

    return () => socket.close()
  }, [player])

  function appendLog(entry: string) {
    setLog((current) => [`${new Date().toLocaleTimeString()} ${entry}`, ...current].slice(0, 20))
  }

  function applySocketMessage(message: PlayerSocketMessage) {
    appendLog(message.type)
    if (message.type === 'PLAYER_STATE_SYNC') {
      setInventory(message.snapshot.inventory)
      setOwnedCharacters(message.snapshot.ownedCharacters)
    } else {
      setCombat(message.snapshot)
    }
  }

  const actions = useMemo(
    () => ({
      async createPlayer(name: string) {
        setLoading(true)
        setError(null)
        try {
          const created = await createPlayer(name)
          const [reloadedPlayer, playerTeams, playerInventory] = await Promise.all([
            getPlayer(created.id),
            getPlayerTeams(created.id),
            getPlayerInventory(created.id),
          ])
          setPlayer(reloadedPlayer)
          setTeams(playerTeams)
          setInventory(playerInventory)
          appendLog(`PLAYER_CREATED ${created.id}`)
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
          const [reloadedPlayer, playerTeams, playerInventory] = await Promise.all([
            getPlayer(player.id),
            getPlayerTeams(player.id),
            getPlayerInventory(player.id),
          ])
          setPlayer(reloadedPlayer)
          setTeams(playerTeams)
          setInventory(playerInventory)
          appendLog(`PLAYER_REFRESH ${player.id}`)
        } catch (caught) {
          setError(extractMessage(caught))
        } finally {
          setLoading(false)
        }
      },
      async assignCharacter(teamId: string, characterKey: string) {
        setLoading(true)
        setError(null)
        try {
          const updatedTeam = await assignCharacterToTeam(teamId, characterKey)
          setTeams((current) => current.map((team) => (team.id === updatedTeam.id ? updatedTeam : team)))
          appendLog(`TEAM_ASSIGN ${characterKey}`)
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
          appendLog(`TEAM_ACTIVE ${teamId}`)
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
        setLoading(true)
        setError(null)
        try {
          const snapshot = await startCombat(player.id)
          setCombat(snapshot)
          appendLog(`COMBAT_START ${player.id}`)
        } catch (caught) {
          setError(extractMessage(caught))
        } finally {
          setLoading(false)
        }
      },
      clearError() {
        setError(null)
      },
    }),
    [player],
  )

  return {
    player,
    teams,
    inventory,
    ownedCharacters,
    templates,
    combat,
    socketStatus,
    log,
    loading,
    error,
    actions,
  }
}

function extractMessage(caught: unknown) {
  if (caught instanceof Error) {
    return caught.message
  }
  return 'Unexpected frontend error.'
}
