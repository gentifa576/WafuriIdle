import { useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import type { EquipmentSlot } from '../core/types/api'
import { CombatViewport } from '../features/combat/components/CombatViewport'
import { reviveSecondsRemaining, toCombatHud } from '../features/combat/model/combatHud'
import type { ClientInventoryItem } from '../features/session/model/clientModels'
import { useGameClient } from '../features/session/hooks/useGameClient'

type WorkspaceView = 'combat' | 'characters' | 'team' | 'inventory' | 'gacha'
type AuthMode = 'guest' | 'signup' | 'login'

export function CombatScreen() {
  const [authMode, setAuthMode] = useState<AuthMode>('guest')
  const [playerName, setPlayerName] = useState('')
  const [authEmail, setAuthEmail] = useState('')
  const [authPassword, setAuthPassword] = useState('')
  const [selectedStarterKey, setSelectedStarterKey] = useState('')
  const [activeView, setActiveView] = useState<WorkspaceView>('combat')
  const [notificationsOpen, setNotificationsOpen] = useState(false)
  const [guestNamePlaceholder, setGuestNamePlaceholder] = useState(() => randomGuestName())
  const [selectedTeamId, setSelectedTeamId] = useState('')
  const [countdownNowMs, setCountdownNowMs] = useState(() => Date.now())
  const downSnapshotStartedAtMs = useRef<number | null>(null)
  const {
    player,
    teams,
    inventory,
    ownedCharacters,
    zoneProgress,
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
  } = useGameClient()
  const activeTeam = useMemo(() => teams.find((team) => team.id === player?.activeTeamId) ?? teams[0] ?? null, [player?.activeTeamId, teams])
  const selectedTeam = useMemo(() => teams.find((team) => team.id === selectedTeamId) ?? activeTeam ?? teams[0] ?? null, [activeTeam, selectedTeamId, teams])
  const selectedStarter = useMemo(
    () => starterTemplates.find((template) => template.key === selectedStarterKey) ?? starterTemplates[0] ?? null,
    [selectedStarterKey, starterTemplates],
  )
  const activeTeamId = activeTeam?.id ?? ''
  const selectedSlots = selectedTeam?.slots ?? emptySlots()
  const activeSlotCount = selectedSlots.filter((slot) => slot.characterKey != null).length
  const ownedCharacterNames = useMemo(
    () =>
      new Map(
        ownedCharacters.map((character) => [character.key, character.name]),
      ),
    [ownedCharacters],
  )
  const combatMembersByKey = useMemo(
    () => new Map((combat?.members ?? []).map((member) => [member.characterKey, member])),
    [combat?.members],
  )
  const memberLabels = useMemo(() => Object.fromEntries(ownedCharacters.map((character) => [character.key, character.name])), [ownedCharacters])
  const topZone = zoneProgress[0] ?? null
  const needsStarterChoice = player?.hasStarterChoice === true
  const displayedPendingReviveMillis =
    combat?.status === 'DOWN'
      ? Math.min(
          30_000,
          combat.pendingReviveMillis + Math.max(0, countdownNowMs - (downSnapshotStartedAtMs.current ?? countdownNowMs)),
        )
      : combat?.pendingReviveMillis ?? 0
  const reviveSeconds = reviveSecondsRemaining(displayedPendingReviveMillis)
  const hud =
    combat?.status === 'DOWN'
      ? {
          ...toCombatHud(combat),
          subtitle: `DOWN · Revive in ${reviveSeconds}s`,
        }
      : toCombatHud(combat)

  useEffect(() => {
    if (selectedTeamId && teams.some((team) => team.id === selectedTeamId)) {
      return
    }
    setSelectedTeamId(activeTeam?.id ?? teams[0]?.id ?? '')
  }, [activeTeam?.id, selectedTeamId, teams])

  useEffect(() => {
    if (selectedStarterKey && starterTemplates.some((template) => template.key === selectedStarterKey)) {
      return
    }
    setSelectedStarterKey(starterTemplates[0]?.key ?? '')
  }, [selectedStarterKey, starterTemplates])

  useEffect(() => {
    if (combat?.status !== 'DOWN') {
      downSnapshotStartedAtMs.current = null
      return
    }

    downSnapshotStartedAtMs.current = Date.now()
    setCountdownNowMs(Date.now())
    const intervalId = window.setInterval(() => {
      setCountdownNowMs(Date.now())
    }, 1000)
    return () => {
      window.clearInterval(intervalId)
    }
  }, [combat?.playerId, combat?.status, combat?.pendingReviveMillis])

  if (!player) {
    return (
      <main className="hud-shell onboarding-shell compact-auth-shell">
        <section className="onboarding-hero">
          <p className="eyebrow">WafuriIdle</p>
          <h1>Battle, drift offline, return richer.</h1>
          <p className="hero-copy">
            This client is now aligned with the real backend contract. Create a guest adventurer, open the live socket,
            and let the server own the fight.
          </p>
        </section>

        <section className="auth-panel panel">
          <div className="section-heading">
            <div>
              <p className="eyebrow">Access</p>
              <h2>Enter the game</h2>
            </div>
            <div className="header-chip">Server-authoritative</div>
          </div>

          <div className="auth-mode-row">
            <button aria-pressed={authMode === 'guest'} className={authModeClass(authMode, 'guest')} onClick={() => setAuthMode('guest')} type="button">
              Guest
            </button>
            <button aria-pressed={authMode === 'signup'} className={authModeClass(authMode, 'signup')} onClick={() => setAuthMode('signup')} type="button">
              Sign Up
            </button>
            <button aria-pressed={authMode === 'login'} className={authModeClass(authMode, 'login')} onClick={() => setAuthMode('login')} type="button">
              Log In
            </button>
          </div>

          <label className="field">
            <span>{authMode === 'login' ? 'Name or email' : 'Player name'}</span>
            <input autoComplete={authMode === 'login' ? 'username' : 'nickname'} placeholder={guestNamePlaceholder} value={playerName} onChange={(event) => setPlayerName(event.target.value)} />
          </label>

          {authMode === 'signup' ? (
            <label className="field">
              <span>Email</span>
              <input autoComplete="email" placeholder="you@example.com" value={authEmail} onChange={(event) => setAuthEmail(event.target.value)} />
            </label>
          ) : null}

          {authMode !== 'guest' ? (
            <label className="field">
              <span>Password</span>
              <input autoComplete={authMode === 'signup' ? 'new-password' : 'current-password'} type="password" value={authPassword} onChange={(event) => setAuthPassword(event.target.value)} />
            </label>
          ) : null}

          {authMode === 'guest' ? (
            <div className="button-row">
              <button
                className="primary-cta"
                disabled={loading}
                onClick={() => {
                  const nextName = playerName.trim() || guestNamePlaceholder
                  void actions.createPlayer(nextName)
                  setGuestNamePlaceholder(randomGuestName())
                }}
                type="button"
              >
                {loading ? 'Entering...' : 'Create Guest'}
              </button>
              <button className="ghost-button" disabled={loading} onClick={() => setGuestNamePlaceholder(randomGuestName())} type="button">
                Randomize
              </button>
            </div>
          ) : null}

          {authMode === 'signup' ? (
            <button
              className="primary-cta"
              disabled={loading || playerName.trim().length === 0 || authPassword.trim().length === 0}
              onClick={() => void actions.signUp(playerName.trim(), authEmail.trim() || null, authPassword)}
              type="button"
            >
              {loading ? 'Creating account...' : 'Sign Up'}
            </button>
          ) : null}

          {authMode === 'login' ? (
            <button
              className="primary-cta"
              disabled={loading || playerName.trim().length === 0 || authPassword.trim().length === 0}
              onClick={() => void actions.login(playerName.trim(), authPassword)}
              type="button"
            >
              {loading ? 'Signing in...' : 'Log In'}
            </button>
          ) : null}

          {error ? (
            <div aria-live="assertive" className="error-banner" role="alert">
              <span>{error}</span>
              <button className="ghost-button" onClick={() => actions.clearError()} type="button">
                Clear
              </button>
            </div>
          ) : null}

          <div className="onboarding-footnote">
            <span>Combat start is now routed through WebSocket ownership.</span>
            <span>Offline progression and zone notifications surface here once connected.</span>
          </div>
        </section>
      </main>
    )
  }

  return (
    <main className="hud-shell app-workspace">
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
          <article className="status-card">
            <span className="label">Socket</span>
            <strong>{socketStatus}</strong>
          </article>
          <article className="status-card">
            <span className="label">Combat</span>
            <strong>{combat?.status ?? 'idle'}</strong>
          </article>
          <article className="status-card">
            <span className="label">Active Team</span>
            <strong>{activeTeam?.id ? `Team ${activeTeam.shortLabel}` : 'None'}</strong>
          </article>
          <article className="status-card">
            <span className="label">Session</span>
            <strong>{sessionExpiresAt ? formatTime(sessionExpiresAt) : 'n/a'}</strong>
          </article>
        </div>

        <div className="status-tools">
          <div className="status-tools-row">
            <button
              aria-controls="notification-popover"
              aria-expanded={notificationsOpen}
              className="notification-toggle ghost-button"
              onClick={() => setNotificationsOpen((current) => !current)}
              type="button"
            >
              <span className="notification-bell">Alerts</span>
              {notifications.length > 0 ? <span className="notification-dot" /> : null}
            </button>
            <button className="ghost-button" onClick={() => void actions.logout()} disabled={loading} type="button">
              Log Out
            </button>
          </div>

          {notificationsOpen ? (
            <div aria-label="Recent alerts" className="notification-popover" id="notification-popover" role="region">
              <div className="section-heading">
                <div>
                  <p className="eyebrow">Notifications</p>
                  <h2>Recent Alerts</h2>
                </div>
                <span className="section-count">{notifications.length}</span>
              </div>
              <div className="notification-list">
                {notifications.length === 0 ? <p className="muted">No pending notifications.</p> : null}
                {notifications.map((notification) => (
                  <article className={`notification-card tone-${notification.tone}`} key={notification.id}>
                    <div>
                      <strong>{notification.title}</strong>
                      <p>{notification.detail}</p>
                      <span>{formatTime(notification.at)}</span>
                    </div>
                    <button aria-label={`Dismiss ${notification.title}`} className="ghost-button slim" onClick={() => actions.dismissNotification(notification.id)} type="button">
                      Dismiss
                    </button>
                  </article>
                ))}
              </div>
            </div>
          ) : null}
        </div>
      </header>

      <section className="workspace-shell">
        <aside className="workspace-nav panel">
          <div className="section-heading">
            <div>
              <p className="eyebrow">Menu</p>
              <h2>Activities</h2>
            </div>
          </div>

          <nav className="nav-list">
            <button aria-pressed={activeView === 'combat'} className={navClass(activeView, 'combat')} onClick={() => setActiveView('combat')} type="button">
              <span>Combat</span>
              <small>{combat?.status ?? 'Idle'}</small>
            </button>
            <button aria-pressed={activeView === 'characters'} className={navClass(activeView, 'characters')} onClick={() => setActiveView('characters')} type="button">
              <span>Characters</span>
              <small>{ownedCharacters.length} unlocked</small>
            </button>
            <button aria-pressed={activeView === 'team'} className={navClass(activeView, 'team')} onClick={() => setActiveView('team')} type="button">
              <span>Team</span>
              <small>{selectedTeam ? `Editing ${selectedTeam.shortLabel}` : 'No team'}</small>
            </button>
            <button aria-pressed={activeView === 'inventory'} className={navClass(activeView, 'inventory')} onClick={() => setActiveView('inventory')} type="button">
              <span>Inventory</span>
              <small>{inventory.length} items</small>
            </button>
            <button aria-pressed={activeView === 'gacha'} className={navClass(activeView, 'gacha')} onClick={() => setActiveView('gacha')} type="button">
              <span>Gacha</span>
              <small>{player.gold} gold ready</small>
            </button>
          </nav>

          <section className="nav-footnote">
            <span className="label">General Info</span>
            <strong>{combat?.zoneId ?? topZone?.zoneId ?? 'starter-plains'}</strong>
            <p>{activity.length} recent activity entries</p>
          </section>
        </aside>

        <section className="workspace-main panel">
          {activeView === 'combat' ? (
            <section className="workspace-section">
              <div className="section-heading">
                <div>
                  <p className="eyebrow">Encounter</p>
                  <h2>{hud.title}</h2>
                </div>
                <div className="header-chip">{hud.subtitle}</div>
              </div>

              <div className="combat-bar-row combat-telemetry">
                <div>
                  <span className="label">Team DPS</span>
                  <strong>{combat?.teamDps.toFixed(1) ?? '0.0'}</strong>
                </div>
                <div>
                  <span className="label">Team HP</span>
                  <strong>{hud.teamCurrentHp.toFixed(0)} / {hud.teamMaxHp.toFixed(0)}</strong>
                </div>
                <div>
                  <span className="label">Enemy ATK</span>
                  <strong>{combat?.enemyAttack.toFixed(1) ?? '0.0'}</strong>
                </div>
                <div>
                  <span className="label">Zone</span>
                  <strong>{combat?.zoneId ?? topZone?.zoneId ?? 'starter-plains'}</strong>
                </div>
              </div>

              <div className="combat-viewport-shell workspace-stage">
                <CombatViewport snapshot={combat} memberLabels={memberLabels} />
              </div>
            </section>
          ) : null}

          {activeView === 'characters' ? (
            <section className="workspace-section">
              <div className="section-heading">
                <div>
                  <p className="eyebrow">Roster</p>
                  <h2>Character Unlocks</h2>
                </div>
                <span className="section-count">{ownedCharacters.length}</span>
              </div>

              <div className="card-grid">
                {ownedCharacters.map((character) => (
                  <article className="workspace-card" key={character.key}>
                    <span className="label">{character.key}</span>
                    <strong>{character.name}</strong>
                    <p>Level {character.level}</p>
                  </article>
                ))}
              </div>
            </section>
          ) : null}

          {activeView === 'gacha' ? (
            <section className="workspace-section">
              <div className="section-heading">
                <div>
                  <p className="eyebrow">Recruitment</p>
                  <h2>Character Pull</h2>
                </div>
                <div className="header-chip">250 gold</div>
              </div>

              <article className="workspace-card gacha-card">
                <p>Every loaded character currently has an even pull chance. Duplicate pulls convert into essence.</p>
                <div className="gacha-stats">
                  <strong>Gold: {player.gold}</strong>
                  <strong>Essence: {player.essence}</strong>
                </div>
                <div className="button-row">
                  <button className="primary-cta" disabled={loading} onClick={() => void actions.pullCharacter(1)} type="button">
                    {loading ? 'Pulling...' : 'Pull x1'}
                  </button>
                  <button className="secondary-button" disabled={loading} onClick={() => void actions.pullCharacter(10)} type="button">
                    {loading ? 'Pulling...' : 'Pull x10'}
                  </button>
                </div>
                {latestPullResult ? (
                  <div className="gacha-result">
                    <strong>Result</strong>
                    <p>Count: {latestPullResult.count}</p>
                    <p>Unlocks: {latestPullResult.unlockedCount}</p>
                    <p>Essence gained: {latestPullResult.totalEssenceGranted}</p>
                    <p>Pulled: {latestPullResult.pulledCharacterKeys.join(', ')}</p>
                  </div>
                ) : (
                  <p className="muted">No pull yet.</p>
                )}
              </article>
            </section>
          ) : null}

          {activeView === 'team' ? (
            <section className="workspace-section">
              <div className="section-heading">
                <div>
                  <p className="eyebrow">Formation</p>
                  <h2>Team Editor</h2>
                </div>
                <span className="section-count">{activeSlotCount}/3</span>
              </div>

              <label className="field">
                <span>Editing team</span>
                <select value={selectedTeam?.id ?? ''} onChange={(event) => setSelectedTeamId(event.target.value)}>
                  {teams.map((team, index) => (
                    <option key={team.id} value={team.id}>
                      Team {index + 1}{team.id === activeTeamId ? ' · Active' : ''}
                    </option>
                  ))}
                </select>
              </label>

              <div className="slot-grid team-grid">
                {selectedSlots.map((slot) => {
                  const options = availableCharactersForSlot(slot.position, selectedSlots, ownedCharacters)
                  const teamId = selectedTeam?.id ?? ''
                  const weaponOptions = availableItemsForSlot(inventory, teamId, slot.position, 'WEAPON')
                  const armorOptions = availableItemsForSlot(inventory, teamId, slot.position, 'ARMOR')
                  const accessoryOptions = availableItemsForSlot(inventory, teamId, slot.position, 'ACCESSORY')
                  return (
                    <article className="slot-card" key={slot.position}>
                      <div className="slot-heading">
                        <span className="label">Slot {slot.position}</span>
                        <strong>{slot.characterKey ? ownedCharacterNames.get(slot.characterKey) ?? slot.characterKey : 'Empty'}</strong>
                      </div>
                      <select
                        aria-label={`Assign character to slot ${slot.position}`}
                        value=""
                        disabled={loading || options.length === 0 || !selectedTeam}
                        onChange={(event) => {
                          if (!selectedTeam || !event.target.value) {
                            return
                          }
                          void actions.assignCharacter(selectedTeam.id, slot.position, event.target.value)
                          event.target.value = ''
                        }}
                      >
                        <option value="">Assign character</option>
                        {options.map((character) => (
                          <option key={character.key} value={character.key}>
                            {character.name} · Lv.{character.level}
                          </option>
                        ))}
                      </select>

                      <EquipmentPicker
                        label="Weapon"
                        equippedItem={inventory.find((item) => item.id === slot.weaponItemId) ?? null}
                        options={weaponOptions}
                        disabled={loading || !selectedTeam || slot.characterKey == null}
                        onEquip={(inventoryItemId) => void actions.equipItem(selectedTeam!.id, slot.position, inventoryItemId, 'WEAPON')}
                        onUnequip={() => void actions.unequipItem(selectedTeam!.id, slot.position, 'WEAPON')}
                      />
                      <EquipmentPicker
                        label="Armor"
                        equippedItem={inventory.find((item) => item.id === slot.armorItemId) ?? null}
                        options={armorOptions}
                        disabled={loading || !selectedTeam || slot.characterKey == null}
                        onEquip={(inventoryItemId) => void actions.equipItem(selectedTeam!.id, slot.position, inventoryItemId, 'ARMOR')}
                        onUnequip={() => void actions.unequipItem(selectedTeam!.id, slot.position, 'ARMOR')}
                      />
                      <EquipmentPicker
                        label="Accessory"
                        equippedItem={inventory.find((item) => item.id === slot.accessoryItemId) ?? null}
                        options={accessoryOptions}
                        disabled={loading || !selectedTeam || slot.characterKey == null}
                        onEquip={(inventoryItemId) => void actions.equipItem(selectedTeam!.id, slot.position, inventoryItemId, 'ACCESSORY')}
                        onUnequip={() => void actions.unequipItem(selectedTeam!.id, slot.position, 'ACCESSORY')}
                      />
                    </article>
                  )
                })}
              </div>

              <div className="button-row workspace-actions">
                <button className="secondary-button" disabled={loading || !selectedTeam} onClick={() => void actions.activateTeam(selectedTeam!.id)} type="button">
                  Set As Active Team
                </button>
              </div>
            </section>
          ) : null}

          {activeView === 'inventory' ? (
            <section className="workspace-section">
              <div className="section-heading">
                <div>
                  <p className="eyebrow">Storage</p>
                  <h2>Inventory</h2>
                </div>
                <span className="section-count">{inventory.length}</span>
              </div>

              <div className="card-grid inventory-grid">
                {inventory.length === 0 ? <p className="muted">Loot will drop here once combat starts paying out.</p> : null}
                {inventory.map((item) => (
                  <ItemHoverCard item={item} key={item.id}>
                    <article className="workspace-card inventory-wide-card">
                      <strong>{item.itemDisplayName}</strong>
                      <p>{item.equippedTeamId ? `Equipped to slot ${item.equippedPosition}` : 'In backpack'}</p>
                    </article>
                  </ItemHoverCard>
                ))}
              </div>
            </section>
          ) : null}
        </section>

        <aside className="workspace-context panel">
          {activeView === 'combat' ? (
            <section className="workspace-section">
              <div className="section-heading">
                <div>
                  <p className="eyebrow">Context</p>
                  <h2>Combat Intel</h2>
                </div>
              </div>

              <div className="stack-panel">
                <div className="button-row workspace-actions context-actions">
                  <button className="primary-cta" disabled={loading || !activeTeamId} onClick={() => void actions.startCombat()} type="button">
                    Start Combat
                  </button>
                  <button className="ghost-button" disabled={loading || combat == null} onClick={() => void actions.stopCombat()} type="button">
                    Stop Combat
                  </button>
                  <button className="ghost-button" disabled={loading} onClick={() => void actions.refreshPlayer()} type="button">
                    Refresh State
                  </button>
                </div>
                <article className="workspace-card">
                  <span className="label">Enemy</span>
                  <strong>{combat?.enemyName ?? 'Awaiting spawn'}</strong>
                  <p>
                    {combat?.status ?? 'Idle'} state
                    {combat?.status === 'DOWN'
                      ? ` · revives in ${reviveSeconds}s`
                      : ` · retaliates for ${combat?.enemyAttack.toFixed(1) ?? '0.0'}`}
                  </p>
                </article>
                <section className="workspace-card">
                  <span className="label">Active Team</span>
                  <strong>{(activeTeam?.occupiedSlots ?? 0)}/3 ready</strong>
                  <div className="combat-member-list">
                    {(activeTeam?.slots ?? emptySlots()).map((slot) => {
                      if (slot.characterKey == null) {
                        return (
                          <article className="combat-member-card is-empty" key={slot.position}>
                            <span className="label">Slot {slot.position}</span>
                            <strong>Empty slot</strong>
                            <p>Assign a character in the Team view to use this slot.</p>
                          </article>
                        )
                      }

                      const member = combatMembersByKey.get(slot.characterKey)
                      return (
                        <article className="combat-member-card" key={slot.position}>
                          <span className="label">Slot {slot.position}</span>
                          <strong>{ownedCharacterNames.get(slot.characterKey) ?? slot.characterKey}</strong>
                          {member ? (
                            <>
                              <p>HP {member.hpLabel}</p>
                              <p>ATK {member.attack.toFixed(1)} · HIT {member.hit.toFixed(1)}</p>
                              <p>{member.alive ? 'Alive' : 'Down'}</p>
                            </>
                          ) : (
                            <p>Awaiting combat sync.</p>
                          )}
                        </article>
                      )
                    })}
                  </div>
                </section>
              </div>
            </section>
          ) : null}

          {activeView === 'characters' ? (
            <section className="workspace-section">
              <div className="section-heading">
                <div>
                  <p className="eyebrow">Context</p>
                  <h2>Character Notes</h2>
                </div>
              </div>

              <div className="stack-panel">
                <article className="workspace-card">
                  <span className="label">Unlocked</span>
                  <strong>{ownedCharacters.length}</strong>
                  <p>Owned characters currently share the player level.</p>
                </article>
                <article className="workspace-card">
                  <span className="label">Starting Roster</span>
                  <strong>{ownedCharacters[0]?.name ?? 'Warrior'}</strong>
                  <p>Future character details can live here.</p>
                </article>
              </div>
            </section>
          ) : null}

          {activeView === 'team' ? (
            <section className="workspace-section">
              <div className="section-heading">
                <div>
                  <p className="eyebrow">Context</p>
                  <h2>Team Notes</h2>
                </div>
              </div>

              <div className="stack-panel">
                <article className="workspace-card">
                  <span className="label">Active Team</span>
                  <strong>{activeTeam?.id ? activeTeam.shortLabel : 'None'}</strong>
                  <p>{selectedTeam?.id === activeTeam?.id ? 'You are editing the active team.' : 'You are editing a reserve team.'}</p>
                </article>
                <article className="workspace-card">
                  <span className="label">Actions</span>
                  <strong>Characters and gear</strong>
                  <p>A character cannot appear twice in the same team, and equipped items stay locked to their assigned slot.</p>
                </article>
              </div>
            </section>
          ) : null}

          {activeView === 'inventory' ? (
            <section className="workspace-section">
              <div className="section-heading">
                <div>
                  <p className="eyebrow">Context</p>
                  <h2>Inventory Notes</h2>
                </div>
              </div>

              <div className="stack-panel">
                <article className="workspace-card">
                  <span className="label">Equippable</span>
                  <strong>{inventory.filter((item) => item.equippedTeamId == null).length}</strong>
                  <p>Backpack items can be assigned from the Team view.</p>
                </article>
                <article className="workspace-card">
                  <span className="label">Equipped</span>
                  <strong>{inventory.filter((item) => item.equippedTeamId != null).length}</strong>
                  <p>Hover an item card to inspect its details.</p>
                </article>
              </div>
            </section>
          ) : null}
        </aside>
      </section>

      {error ? (
        <div aria-live="assertive" className="floating-error" role="alert">
          <span>{error}</span>
          <button className="ghost-button" onClick={() => actions.clearError()} type="button">
            Clear
          </button>
        </div>
      ) : null}

      {needsStarterChoice ? (
        <div className="starter-modal-backdrop">
          <section aria-describedby="starter-modal-description" aria-labelledby="starter-modal-title" aria-modal="true" className="starter-modal panel" role="dialog">
            <div className="section-heading">
              <div>
                <p className="eyebrow">Starter Selection</p>
                <h2 id="starter-modal-title">Choose Your First Character</h2>
              </div>
            </div>
            <p className="muted" id="starter-modal-description">Pick one starter to begin. This prompt will remain until your roster is no longer empty.</p>
            <div className="starter-choice-grid">
              {starterTemplates.map((starter) => (
                <button
                  aria-pressed={selectedStarter?.key === starter.key}
                  className={starterChoiceClass(selectedStarter?.key ?? '', starter.key)}
                  key={starter.key}
                  onClick={() => setSelectedStarterKey(starter.key)}
                  type="button"
                >
                  <strong>{starter.name}</strong>
                  <span>{starter.key}</span>
                </button>
              ))}
            </div>
            <div className="button-row">
              <button
                className="primary-cta"
                disabled={loading || !selectedStarter}
                onClick={() => selectedStarter && void actions.claimStarter(selectedStarter.key)}
                type="button"
              >
                {loading ? 'Claiming...' : 'Confirm Starter'}
              </button>
            </div>
            {error ? <div aria-live="assertive" className="error-banner" role="alert"><span>{error}</span></div> : null}
          </section>
        </div>
      ) : null}
    </main>
  )
}

function navClass(activeView: WorkspaceView, item: WorkspaceView) {
  return activeView === item ? 'nav-item is-active' : 'nav-item'
}

function authModeClass(activeMode: AuthMode, item: AuthMode) {
  return activeMode === item ? 'auth-mode-button is-active' : 'auth-mode-button'
}

function starterChoiceClass(activeKey: string, key: string) {
  return activeKey === key ? 'starter-choice is-active' : 'starter-choice'
}

interface EquipmentPickerProps {
  label: string
  equippedItem: ClientInventoryItem | null
  options: ClientInventoryItem[]
  disabled: boolean
  onEquip: (inventoryItemId: string) => void
  onUnequip: () => void
}

function EquipmentPicker({ label, equippedItem, options, disabled, onEquip, onUnequip }: EquipmentPickerProps) {
  return (
    <div className="equipment-block">
      <div className="equipment-heading">
        <span className="label">{label}</span>
        {equippedItem ? (
          <ItemHoverCard item={equippedItem}>
            <strong>{equippedItem.itemDisplayName}</strong>
          </ItemHoverCard>
        ) : (
          <strong>Empty</strong>
        )}
      </div>
      <select
        aria-label={`${label} selection`}
        value=""
        disabled={disabled || options.length === 0}
        onChange={(event) => {
          if (!event.target.value) {
            return
          }
          onEquip(event.target.value)
          event.target.value = ''
        }}
      >
        <option value="">{options.length === 0 ? 'No item available' : `Equip ${label.toLowerCase()}`}</option>
        {options.map((item) => (
          <option key={item.id} value={item.id}>
            {item.itemDisplayName} · {item.rarity}
          </option>
        ))}
      </select>
      {equippedItem ? (
        <button aria-label={`Unequip ${label}`} className="ghost-button slim" disabled={disabled} onClick={onUnequip} type="button">
          Unequip
        </button>
      ) : null}
    </div>
  )
}

function ItemHoverCard({ item, children }: { item: ClientInventoryItem; children: ReactNode }) {
  const [visible, setVisible] = useState(false)
  const timerRef = useRef<number | null>(null)

  useEffect(
    () => () => {
      if (timerRef.current != null) {
        window.clearTimeout(timerRef.current)
      }
    },
    [],
  )

  return (
    <div
      className="item-hover-anchor"
      onMouseEnter={() => {
        timerRef.current = window.setTimeout(() => setVisible(true), 400)
      }}
      onMouseLeave={() => {
        if (timerRef.current != null) {
          window.clearTimeout(timerRef.current)
          timerRef.current = null
        }
        setVisible(false)
      }}
    >
      {children}
      {visible ? (
        <div className="item-hover-card">
          <strong>{item.itemDisplayName}</strong>
          <p>{item.rarity} · {item.itemType}</p>
          <p>{item.itemBaseStat.type} {item.itemBaseStat.value}</p>
          {item.subStats.length > 0 ? <p>{item.subStats.map((stat) => `${stat.type} ${stat.value}`).join(' · ')}</p> : null}
        </div>
      ) : null}
    </div>
  )
}

function availableCharactersForSlot(position: number, slots: Array<{ position: number; characterKey: string | null }>, ownedCharacters: Array<{ key: string; name: string; level: number }>) {
  const assignedKeys = new Set(
    slots
      .filter((slot) => slot.position !== position)
      .map((slot) => slot.characterKey)
      .filter((key): key is string => key != null),
  )
  return ownedCharacters.filter((character) => !assignedKeys.has(character.key))
}

function availableItemsForSlot(
  inventory: ClientInventoryItem[],
  teamId: string,
  position: number,
  equipmentSlot: EquipmentSlot,
) {
  return inventory.filter(
    (item) =>
      typeof item.itemType === 'string' &&
      item.itemType === equipmentSlot &&
      (item.equippedTeamId == null || (item.equippedTeamId === teamId && item.equippedPosition === position)),
  )
}

function emptySlots() {
  return [
    { position: 1, characterKey: null, weaponItemId: null, armorItemId: null, accessoryItemId: null },
    { position: 2, characterKey: null, weaponItemId: null, armorItemId: null, accessoryItemId: null },
    { position: 3, characterKey: null, weaponItemId: null, armorItemId: null, accessoryItemId: null },
  ]
}

function formatTime(value: string) {
  return new Date(value).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
}

function randomGuestName() {
  const adjectives = ['Amber', 'Cinder', 'Moss', 'Nova', 'Frost', 'Gale', 'Rune', 'Velvet']
  const nouns = ['Wanderer', 'Lancer', 'Drifter', 'Scout', 'Warden', 'Seeker', 'Rider', 'Bloom']
  return `${pick(adjectives)} ${pick(nouns)} ${Math.floor(100 + Math.random() * 900)}`
}

function pick(values: string[]) {
  return values[Math.floor(Math.random() * values.length)] ?? 'Guest'
}
