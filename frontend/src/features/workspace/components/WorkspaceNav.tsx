import type { ActivityEntry } from '../../session/hooks/gameClientTypes'
import type { ClientCombat, ClientTeam, ClientZoneProgress } from '../../session/model/clientModels'
import './workspace.css'

export type WorkspaceView = 'combat' | 'characters' | 'team' | 'inventory' | 'gacha'

interface WorkspaceNavProps {
  activeView: WorkspaceView
  combat: ClientCombat | null
  ownedCharacterCount: number
  selectedTeam: ClientTeam | null
  inventoryCount: number
  playerGold: number
  topZone: ClientZoneProgress | null
  activity: ActivityEntry[]
  onViewChange: (view: WorkspaceView) => void
}

export function WorkspaceNav({
  activeView,
  combat,
  ownedCharacterCount,
  selectedTeam,
  inventoryCount,
  playerGold,
  topZone,
  activity,
  onViewChange,
}: WorkspaceNavProps) {
  return (
    <aside aria-labelledby="workspace-nav-title" className="workspace-nav panel">
      <div className="section-heading">
        <div>
          <p className="eyebrow">Menu</p>
          <h2 id="workspace-nav-title">Activities</h2>
        </div>
      </div>

      <nav aria-label="Primary navigation" className="nav-list">
        <button
          aria-label={`Open combat workspace. Current combat status: ${combat?.status ?? 'Idle'}.`}
          aria-pressed={activeView === 'combat'}
          className={navClass(activeView, 'combat')}
          data-workspace-default-focus="true"
          onClick={() => onViewChange('combat')}
          type="button"
        >
          <span>Combat</span>
          <small>{combat?.status ?? 'Idle'}</small>
        </button>
        <button
          aria-label={`Open characters workspace. ${ownedCharacterCount} characters unlocked.`}
          aria-pressed={activeView === 'characters'}
          className={navClass(activeView, 'characters')}
          onClick={() => onViewChange('characters')}
          type="button"
        >
          <span>Characters</span>
          <small>{ownedCharacterCount} unlocked</small>
        </button>
        <button
          aria-label={`Open team workspace. ${selectedTeam ? `Editing team ${selectedTeam.shortLabel}.` : 'No team selected.'}`}
          aria-pressed={activeView === 'team'}
          className={navClass(activeView, 'team')}
          onClick={() => onViewChange('team')}
          type="button"
        >
          <span>Team</span>
          <small>{selectedTeam ? `Editing ${selectedTeam.shortLabel}` : 'No team'}</small>
        </button>
        <button
          aria-label={`Open inventory workspace. ${inventoryCount} items available.`}
          aria-pressed={activeView === 'inventory'}
          className={navClass(activeView, 'inventory')}
          onClick={() => onViewChange('inventory')}
          type="button"
        >
          <span>Inventory</span>
          <small>{inventoryCount} items</small>
        </button>
        <button
          aria-label={`Open gacha workspace. ${playerGold} gold available.`}
          aria-pressed={activeView === 'gacha'}
          className={navClass(activeView, 'gacha')}
          onClick={() => onViewChange('gacha')}
          type="button"
        >
          <span>Gacha</span>
          <small>{playerGold} gold ready</small>
        </button>
      </nav>

      <section aria-label="General progress" className="nav-footnote">
        <span className="label">General Info</span>
        <strong>{combat?.zoneId ?? topZone?.zoneId ?? 'starter-plains'}</strong>
        <p>{activity.length} recent activity entries</p>
      </section>
    </aside>
  )
}

function navClass(activeView: WorkspaceView, item: WorkspaceView) {
  return activeView === item ? 'nav-item is-active' : 'nav-item'
}
