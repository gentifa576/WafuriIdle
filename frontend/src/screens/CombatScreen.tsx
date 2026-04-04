import { useEffect, useMemo, useRef, useState } from 'react'
import { CombatWorkspace } from '../features/combat/components/CombatWorkspace'
import { GachaWorkspace } from '../features/gacha/components/GachaWorkspace'
import { InventoryWorkspace } from '../features/inventory/components/InventoryWorkspace'
import { reviveSecondsRemaining, toCombatHud } from '../features/combat/model/combatHud'
import { RosterWorkspace } from '../features/roster/components/RosterWorkspace'
import { OnboardingPanel } from '../features/session/components/OnboardingPanel'
import { StarterChoiceDialog } from '../features/session/components/StarterChoiceDialog'
import { useGameClient } from '../features/session/hooks/useGameClient'
import { TeamWorkspace } from '../features/team/components/TeamWorkspace'
import { PlayerStatusHeader } from '../features/workspace/components/PlayerStatusHeader'
import { WorkspaceNav, type WorkspaceView } from '../features/workspace/components/WorkspaceNav'

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
      <OnboardingPanel
        authMode={authMode}
        playerName={playerName}
        authEmail={authEmail}
        authPassword={authPassword}
        guestNamePlaceholder={guestNamePlaceholder}
        loading={loading}
        error={error}
        onAuthModeChange={setAuthMode}
        onPlayerNameChange={setPlayerName}
        onAuthEmailChange={setAuthEmail}
        onAuthPasswordChange={setAuthPassword}
        onCreateGuest={() => {
          const nextName = playerName.trim() || guestNamePlaceholder
          void actions.createPlayer(nextName)
          setGuestNamePlaceholder(randomGuestName())
        }}
        onRandomizeGuestName={() => setGuestNamePlaceholder(randomGuestName())}
        onSignUp={() => void actions.signUp(playerName.trim(), authEmail.trim() || null, authPassword)}
        onLogin={() => void actions.login(playerName.trim(), authPassword)}
        onClearError={() => actions.clearError()}
      />
    )
  }

  return (
    <main className="hud-shell app-workspace">
      <PlayerStatusHeader
        player={player}
        topZone={topZone}
        socketStatus={socketStatus}
        combat={combat}
        activeTeam={activeTeam}
        sessionExpiresAt={sessionExpiresAt}
        notificationsOpen={notificationsOpen}
        notifications={notifications}
        loading={loading}
        onToggleNotifications={() => setNotificationsOpen((current) => !current)}
        onLogout={() => void actions.logout()}
        onDismissNotification={(id) => actions.dismissNotification(id)}
      />

      <section className="workspace-shell">
        <WorkspaceNav
          activeView={activeView}
          combat={combat}
          ownedCharacterCount={ownedCharacters.length}
          selectedTeam={selectedTeam}
          inventoryCount={inventory.length}
          playerGold={player.gold}
          topZone={topZone}
          activity={activity}
          onViewChange={setActiveView}
        />

        {activeView === 'combat' ? (
          <CombatWorkspace
            hud={hud}
            combat={combat}
            memberLabels={memberLabels}
            topZone={topZone}
            activeTeam={activeTeam}
            combatMembersByKey={combatMembersByKey}
            ownedCharacterNames={ownedCharacterNames}
            reviveSeconds={reviveSeconds}
            loading={loading}
            activeTeamId={activeTeamId}
            onStartCombat={() => void actions.startCombat()}
            onStopCombat={() => void actions.stopCombat()}
            onRefreshState={() => void actions.refreshPlayer()}
          />
        ) : null}

        {activeView === 'characters' ? <RosterWorkspace ownedCharacters={ownedCharacters} /> : null}

        {activeView === 'team' ? (
          <TeamWorkspace
            teams={teams}
            selectedTeam={selectedTeam}
            activeTeamId={activeTeamId}
            inventory={inventory}
            ownedCharacters={ownedCharacters}
            ownedCharacterNames={ownedCharacterNames}
            activeTeam={activeTeam}
            loading={loading}
            onTeamChange={setSelectedTeamId}
            onAssignCharacter={(teamId, position, characterKey) => void actions.assignCharacter(teamId, position, characterKey)}
            onEquipItem={(teamId, position, inventoryItemId, slot) => void actions.equipItem(teamId, position, inventoryItemId, slot)}
            onUnequipItem={(teamId, position, slot) => void actions.unequipItem(teamId, position, slot)}
            onActivateTeam={(teamId) => void actions.activateTeam(teamId)}
          />
        ) : null}

        {activeView === 'inventory' ? <InventoryWorkspace inventory={inventory} /> : null}

        {activeView === 'gacha' ? (
          <GachaWorkspace player={player} latestPullResult={latestPullResult} loading={loading} onPullCharacter={(count) => void actions.pullCharacter(count)} />
        ) : null}
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
        <StarterChoiceDialog
          starterTemplates={starterTemplates}
          selectedStarterKey={selectedStarterKey}
          loading={loading}
          error={error}
          onSelectStarter={setSelectedStarterKey}
          onConfirmStarter={() => selectedStarter && void actions.claimStarter(selectedStarter.key)}
        />
      ) : null}
    </main>
  )
}

function randomGuestName() {
  const adjectives = ['Amber', 'Cinder', 'Moss', 'Nova', 'Frost', 'Gale', 'Rune', 'Velvet']
  const nouns = ['Wanderer', 'Lancer', 'Drifter', 'Scout', 'Warden', 'Seeker', 'Rider', 'Bloom']
  return `${pick(adjectives)} ${pick(nouns)} ${Math.floor(100 + Math.random() * 900)}`
}

function pick(values: string[]) {
  return values[Math.floor(Math.random() * values.length)] ?? 'Guest'
}
