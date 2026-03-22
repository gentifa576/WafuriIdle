import { useMemo, useState } from 'react'
import { CombatViewport } from '../features/combat/components/CombatViewport'
import { toCombatHud } from '../features/combat/model/combatHud'
import { useGameClient } from '../features/session/hooks/useGameClient'

export function CombatScreen() {
  const [playerName, setPlayerName] = useState('Player One')
  const [selectedTeamId, setSelectedTeamId] = useState('')
  const [selectedCharacterKey, setSelectedCharacterKey] = useState('')
  const {
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
  } = useGameClient()
  const hud = toCombatHud(combat)
  const selectedTeam = useMemo(
    () => teams.find((team) => team.id === (selectedTeamId || player?.activeTeamId || '')) ?? null,
    [player?.activeTeamId, selectedTeamId, teams],
  )

  const availableOwnedCharacters = useMemo(() => {
    const assignedKeys = new Set(selectedTeam?.characterKeys ?? [])
    return ownedCharacters.filter((character) => !assignedKeys.has(character.key))
  }, [ownedCharacters, selectedTeam?.characterKeys])

  const ownedCharacterNames = useMemo(
    () =>
      new Map(
        ownedCharacters.map((character) => [character.key, character.name]),
      ),
    [ownedCharacters],
  )
  const activeTeamId = selectedTeamId || player?.activeTeamId || ''
  const playerLabel = player?.name ?? 'No player connected'
  const navItems = ['Overview', 'Teams', 'Combat', 'Roster', 'Inventory', 'Templates']
  const primaryLog = log.slice(0, 8)

  return (
    <div className="game-shell">
      <header className="game-topbar panel">
        <div className="topbar-brand">
          <p className="eyebrow">WafuriIdle</p>
          <h1>Combat Debug Client</h1>
        </div>
        <div className="topbar-status">
          <article className="top-stat">
            <span className="label">Player</span>
            <strong>{playerLabel}</strong>
          </article>
          <article className="top-stat">
            <span className="label">Socket</span>
            <strong>{socketStatus}</strong>
          </article>
          <article className="top-stat">
            <span className="label">Combat</span>
            <strong>{hud.subtitle}</strong>
          </article>
          <article className="top-stat">
            <span className="label">Enemy</span>
            <strong>{hud.title}</strong>
          </article>
        </div>
      </header>

      <div className="game-body">
        <aside className="left-rail panel">
          <div className="panel-header">
            <div>
              <p className="eyebrow">Navigation</p>
              <h2>Command Rail</h2>
            </div>
          </div>
          <nav className="rail-nav">
            {navItems.map((item) => (
              <button key={item} className={`rail-link${item === 'Combat' ? ' active' : ''}`} type="button">
                {item}
              </button>
            ))}
          </nav>
          <div className="rail-card">
            <span className="label">Connected roster</span>
            <strong>{ownedCharacters.length}</strong>
            <span className="muted">Owned characters mirrored from REST and WebSocket state.</span>
          </div>
          <div className="rail-card">
            <span className="label">Inventory slots</span>
            <strong>{inventory.length}</strong>
            <span className="muted">Current client inventory snapshot.</span>
          </div>
        </aside>

        <section className="center-column">
          <section className="command-bar panel">
            <div className="command-fields">
              <label className="field">
                <span>Player name</span>
                <input value={playerName} onChange={(event) => setPlayerName(event.target.value)} />
              </label>
              <label className="field">
                <span>Team</span>
                <select value={activeTeamId} onChange={(event) => setSelectedTeamId(event.target.value)}>
                  <option value="">Select a team</option>
                  {teams.map((team) => (
                    <option key={team.id} value={team.id}>
                      {team.id.slice(0, 8)}{team.id === player?.activeTeamId ? ' · active' : ''}
                    </option>
                  ))}
                </select>
              </label>
              <label className="field">
                <span>Add owned character</span>
                <select value={selectedCharacterKey} onChange={(event) => setSelectedCharacterKey(event.target.value)}>
                  <option value="">Select a character</option>
                  {availableOwnedCharacters.map((character) => (
                    <option key={character.key} value={character.key}>
                      {character.name}
                    </option>
                  ))}
                </select>
              </label>
            </div>
            <div className="button-row">
              <button disabled={loading} onClick={() => void actions.createPlayer(playerName)}>
                Create Player
              </button>
              <button disabled={loading || player == null} onClick={() => void actions.refreshPlayer()}>
                Refresh
              </button>
              <button disabled={loading || !selectedTeam} onClick={() => void actions.activateTeam(selectedTeam!.id)}>
                Activate Team
              </button>
              <button
                disabled={loading || !selectedTeam || !selectedCharacterKey}
                onClick={() => void actions.assignCharacter(selectedTeam!.id, selectedCharacterKey)}
              >
                Add To Team
              </button>
              <button disabled={loading || player == null} onClick={() => void actions.startCombat()}>
                Start Combat
              </button>
            </div>
            {error ? (
              <div className="error-banner">
                <span>{error}</span>
                <button className="ghost-button" onClick={() => actions.clearError()}>
                  Clear
                </button>
              </div>
            ) : null}
          </section>

          <section className="battle-panel panel">
            <div className="panel-header">
              <div>
                <p className="eyebrow">Combat</p>
                <h2>Active Encounter</h2>
              </div>
              <div className="header-chip">Zone {combat?.zoneId ?? 'default'}</div>
            </div>
            <div className="combat-summary">
              <div>
                <span className="label">Enemy HP</span>
                <strong>{combat ? `${combat.enemyHp.toFixed(0)} / ${combat.enemyMaxHp.toFixed(0)}` : 'n/a'}</strong>
              </div>
              <div>
                <span className="label">Team DPS</span>
                <strong>{combat?.teamDps.toFixed(1) ?? '0.0'}</strong>
              </div>
              <div>
                <span className="label">Active team</span>
                <strong>{player?.activeTeamId ? player.activeTeamId.slice(0, 8) : 'none'}</strong>
              </div>
            </div>
            <CombatViewport
              snapshot={combat}
              memberLabels={Object.fromEntries(ownedCharacters.map((character) => [character.key, character.name]))}
            />
            <div className="member-grid">
              {(combat?.members ?? []).map((member) => (
                <article className="member-card" key={member.characterKey}>
                  <strong>{ownedCharacterNames.get(member.characterKey) ?? member.characterKey}</strong>
                  <span>HP {member.currentHp.toFixed(0)} / {member.maxHp.toFixed(0)}</span>
                  <span>DPS {(member.attack * member.hit).toFixed(1)}</span>
                </article>
              ))}
              {(combat?.members.length ?? 0) === 0 ? <p className="muted">No active combat members yet.</p> : null}
            </div>
          </section>

          <section className="bottom-strip panel">
            <div className="panel-header">
              <div>
                <p className="eyebrow">Feed</p>
                <h2>Client Log</h2>
              </div>
              <div className="header-chip">{log.length} entries</div>
            </div>
            <div className="event-log compact">
              {primaryLog.length === 0 ? <p className="muted">No messages yet.</p> : null}
              {primaryLog.map((entry) => (
                <div className="log-entry" key={entry}>
                  {entry}
                </div>
              ))}
            </div>
          </section>
        </section>

        <aside className="right-column">
          <section className="detail-panel panel">
            <div className="panel-subheader">
              <h3>Team Members</h3>
              <span>{selectedTeam?.characterKeys.length ?? 0}</span>
            </div>
            <div className="pill-list">
              {(selectedTeam?.characterKeys ?? []).map((characterKey) => (
                <span className="pill" key={characterKey}>
                  {ownedCharacterNames.get(characterKey) ?? characterKey}
                </span>
              ))}
              {(selectedTeam?.characterKeys.length ?? 0) === 0 ? <span className="muted">No assigned characters.</span> : null}
            </div>
          </section>

          <section className="detail-panel panel">
            <div className="panel-subheader">
              <h3>Inventory</h3>
              <span>{inventory.length}</span>
            </div>
            <div className="inventory-list compact-scroll">
              {inventory.map((item) => (
                <article className="inventory-card" key={item.id}>
                  <strong>{item.itemDisplayName}</strong>
                  <span>{item.rarity} · {item.itemType}</span>
                  <span>{item.itemBaseStat.type} {item.itemBaseStat.value}</span>
                </article>
              ))}
              {inventory.length === 0 ? <span className="muted">Inventory will appear from REST and WS sync.</span> : null}
            </div>
          </section>

          <section className="detail-panel panel">
            <div className="panel-subheader">
              <h3>Loaded Templates</h3>
              <span>{templates.length}</span>
            </div>
            <div className="pill-list">
              {templates.map((template) => (
                <span className="pill dim" key={template.key}>
                  {template.name}
                </span>
              ))}
            </div>
          </section>
        </aside>
      </div>
    </div>
  )
}
