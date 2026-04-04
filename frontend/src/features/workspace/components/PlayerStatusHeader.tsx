import { ConnectionStatusBadge } from './ConnectionStatusBadge'
import { ActionButton } from '../../../shared/ui/ActionButton'
import { SectionHeader } from '../../../shared/ui/SectionHeader'
import { SurfaceCard } from '../../../shared/ui/SurfaceCard'
import type { HudNotification, SocketStatus } from '../../session/hooks/gameClientTypes'
import type { ClientCombat, ClientPlayer, ClientTeam, ClientZoneProgress } from '../../session/model/clientModels'
import './workspace.css'

interface PlayerStatusHeaderProps {
  player: ClientPlayer
  topZone: ClientZoneProgress | null
  socketStatus: SocketStatus
  combat: ClientCombat | null
  activeTeam: ClientTeam | null
  sessionExpiresAt: string | null
  notificationsOpen: boolean
  notifications: HudNotification[]
  loading: boolean
  onToggleNotifications: () => void
  onLogout: () => void
  onDismissNotification: (id: string) => void
}

export function PlayerStatusHeader({
  player,
  topZone,
  socketStatus,
  combat,
  activeTeam,
  sessionExpiresAt,
  notificationsOpen,
  notifications,
  loading,
  onToggleNotifications,
  onLogout,
  onDismissNotification,
}: PlayerStatusHeaderProps) {
  return (
    <header className="top-status panel">
      <div className="status-identity">
        <p className="eyebrow">Active Adventurer</p>
        <h1>{player.name}</h1>
        <p>
          Level {player.level} · {player.experience} EXP · {player.gold} gold · {player.essence} essence
          {topZone ? ` · ${topZone.zoneId} Lv.${topZone.level}` : ' · No zone progress yet'}
        </p>
      </div>

      <div className="status-grid">
        <SurfaceCard className="status-card">
          <span className="label">Socket</span>
          <strong>{socketStatus}</strong>
        </SurfaceCard>
        <SurfaceCard className="status-card">
          <span className="label">Combat</span>
          <strong>{combat?.status ?? 'idle'}</strong>
        </SurfaceCard>
        <SurfaceCard className="status-card">
          <span className="label">Active Team</span>
          <strong>{activeTeam?.id ? `Team ${activeTeam.shortLabel}` : 'None'}</strong>
        </SurfaceCard>
        <SurfaceCard className="status-card">
          <span className="label">Session</span>
          <strong>{sessionExpiresAt ? formatTime(sessionExpiresAt) : 'n/a'}</strong>
        </SurfaceCard>
      </div>

      <div className="status-tools">
        <ConnectionStatusBadge socketStatus={socketStatus} />
        <div className="status-tools-row">
          <ActionButton
            aria-controls="notification-popover"
            aria-expanded={notificationsOpen}
            className="notification-toggle ghost-button"
            onClick={onToggleNotifications}
          >
            <span className="notification-bell">Alerts</span>
            {notifications.length > 0 ? <span className="notification-dot" /> : null}
          </ActionButton>
          <ActionButton disabled={loading} onClick={onLogout}>
            Log Out
          </ActionButton>
        </div>

        {notificationsOpen ? (
          <div aria-label="Recent alerts" className="notification-popover" id="notification-popover" role="region">
            <SectionHeader eyebrow="Notifications" title="Recent Alerts" aside={<span className="section-count">{notifications.length}</span>} />
            <div className="notification-list">
              {notifications.length === 0 ? <p className="muted">No alerts yet. Combat, offline rewards, and command feedback will appear here.</p> : null}
              {notifications.map((notification) => (
                <SurfaceCard className={`notification-card tone-${notification.tone}`} key={notification.id}>
                  <div>
                    <strong>{notification.title}</strong>
                    <p>{notification.detail}</p>
                    <span>{formatTime(notification.at)}</span>
                  </div>
                  <ActionButton aria-label={`Dismiss ${notification.title}`} onClick={() => onDismissNotification(notification.id)} slim>
                    Dismiss
                  </ActionButton>
                </SurfaceCard>
              ))}
            </div>
          </div>
        ) : null}
      </div>
    </header>
  )
}

function formatTime(value: string) {
  return new Date(value).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
}
