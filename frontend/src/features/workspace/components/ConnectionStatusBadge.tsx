import type { SocketStatus } from '../../session/hooks/gameClientTypes'
import './workspace.css'

interface ConnectionStatusBadgeProps {
  socketStatus: SocketStatus
}

const STATUS_COPY: Record<SocketStatus, { label: string; detail: string; tone: 'neutral' | 'warning' | 'danger' | 'success' }> = {
  connected: {
    label: 'Live',
    detail: 'Realtime sync is active.',
    tone: 'success',
  },
  connecting: {
    label: 'Reconnecting',
    detail: 'Trying to restore realtime sync.',
    tone: 'warning',
  },
  disconnected: {
    label: 'Offline',
    detail: 'Realtime sync is unavailable until the socket reconnects.',
    tone: 'danger',
  },
  error: {
    label: 'Socket Error',
    detail: 'Realtime updates may be stale until the connection recovers.',
    tone: 'danger',
  },
}

export function ConnectionStatusBadge({ socketStatus }: ConnectionStatusBadgeProps) {
  const copy = STATUS_COPY[socketStatus]

  return (
    <div aria-label={`Connection status: ${copy.label}. ${copy.detail}`} className={`connection-badge tone-${copy.tone}`}>
      <span className="label">Realtime</span>
      <strong>{copy.label}</strong>
      <p>{copy.detail}</p>
    </div>
  )
}
