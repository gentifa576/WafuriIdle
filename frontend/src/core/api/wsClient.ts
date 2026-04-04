import { env } from '../config/env'
import { currentSessionToken } from './httpClient'
import { parsePlayerSocketMessage, type SocketMessageParseError } from './socketMessages'
import type { PlayerSocketMessage } from '../types/api'

export interface PlayerSocketOptions {
  onMessage: (message: PlayerSocketMessage) => void
  onInvalidMessage?: (error: SocketMessageParseError) => void
  onOpen?: () => void
  onClose?: () => void
  onError?: () => void
}

export function createPlayerSocket(playerId: string, options: PlayerSocketOptions): WebSocket {
  const baseUrl = env.apiBaseUrl || window.location.origin
  const url = new URL(`/ws/player/${playerId}`, baseUrl)
  url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:'
  const token = currentSessionToken()
  const protocols = token ? ['bearer-token-carrier', encodeURIComponent(`quarkus-http-upgrade#Authorization#Bearer ${token}`)] : undefined
  const socket = protocols ? new WebSocket(url, protocols) : new WebSocket(url)
  socket.addEventListener('open', () => options.onOpen?.())
  socket.addEventListener('close', () => options.onClose?.())
  socket.addEventListener('error', () => options.onError?.())
  socket.addEventListener('message', (event) => {
    const result = parsePlayerSocketMessage(event.data)
    if (result.ok) {
      options.onMessage(result.message)
      return
    }
    options.onInvalidMessage?.(result.error)
  })
  return socket
}

export function sendStartCombat(socket: WebSocket) {
  socket.send(JSON.stringify({ type: 'START_COMBAT' }))
}

export function sendStopCombat(socket: WebSocket) {
  socket.send(JSON.stringify({ type: 'STOP_COMBAT' }))
}
