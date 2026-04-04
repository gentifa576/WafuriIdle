import { startTransition, useEffect, useRef, useState } from 'react'
import { createPlayerSocket } from '../../../core/api/wsClient'
import type { SocketMessageParseError } from '../../../core/api/socketMessages'
import type { PlayerSocketMessage } from '../../../core/types/api'
import type { SocketStatus } from './gameClientTypes'

interface UsePlayerSocketOptions {
  playerId: string | null
  onMessage: (message: PlayerSocketMessage) => void
  onInvalidMessage: (error: SocketMessageParseError) => void
}

export function usePlayerSocket(options: UsePlayerSocketOptions) {
  const [socketStatus, setSocketStatus] = useState<SocketStatus>('disconnected')
  const socketRef = useRef<WebSocket | null>(null)
  const handlersRef = useRef({
    onMessage: options.onMessage,
    onInvalidMessage: options.onInvalidMessage,
  })

  handlersRef.current = {
    onMessage: options.onMessage,
    onInvalidMessage: options.onInvalidMessage,
  }

  useEffect(() => {
    if (!options.playerId) {
      socketRef.current?.close()
      socketRef.current = null
      setSocketStatus('disconnected')
      return
    }

    setSocketStatus('connecting')
    const socket = createPlayerSocket(options.playerId, {
      onOpen: () => setSocketStatus('connected'),
      onClose: () => setSocketStatus('disconnected'),
      onError: () => setSocketStatus('error'),
      onMessage: (message) => {
        startTransition(() => {
          handlersRef.current.onMessage(message)
        })
      },
      onInvalidMessage: (error) => {
        startTransition(() => {
          handlersRef.current.onInvalidMessage(error)
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
  }, [options.playerId])

  return {
    socketRef,
    socketStatus,
  }
}
