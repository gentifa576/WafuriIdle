import { env } from '../config/env'
import { parsePlayerSocketMessage } from './socketMessages'
import type { PlayerSocketMessage } from '../types/api'

export interface PlayerSocketOptions {
  onMessage: (message: PlayerSocketMessage) => void
  onOpen?: () => void
  onClose?: () => void
  onError?: () => void
}

export function createPlayerSocket(playerId: string, options: PlayerSocketOptions): WebSocket {
  const baseUrl = env.apiBaseUrl || window.location.origin
  const url = new URL(`/ws/player/${playerId}`, baseUrl)
  url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:'

  const socket = new WebSocket(url)
  socket.addEventListener('open', () => options.onOpen?.())
  socket.addEventListener('close', () => options.onClose?.())
  socket.addEventListener('error', () => options.onError?.())
  socket.addEventListener('message', (event) => {
    const message = parsePlayerSocketMessage(event.data)
    if (message) {
      options.onMessage(message)
    }
  })
  return socket
}
