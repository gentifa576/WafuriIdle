import { useEffect, useMemo, useRef, useState } from 'react'
import type { SocketStatus } from '../../session/hooks/gameClientTypes'
import { FeedbackState } from '../../workspace/components/FeedbackState'
import { ActionButton } from '../../../shared/ui/ActionButton'
import { SectionHeader } from '../../../shared/ui/SectionHeader'
import { SurfaceCard } from '../../../shared/ui/SurfaceCard'
import { CombatViewport } from './CombatViewport'
import type { SkillEffectEvent } from '../../../core/types/api'
import type { ClientCombat, ClientCombatMember, ClientTeam, ClientZoneProgress } from '../../session/model/clientModels'
import './combat.css'

const EMPTY_COMBAT_MEMBERS: ClientCombatMember[] = []

interface CombatWorkspaceProps {
  hud: {
    title: string | null
    subtitle: string
    teamCurrentHp: number
    teamMaxHp: number
  }
  combat: ClientCombat | null
  skillEvents: SkillEffectEvent[]
  memberLabels: Record<string, string>
  memberImages: Record<string, string | null | undefined>
  skillByCharacterKey: Map<string, { name: string; cooldownMillis: number }>
  topZone: ClientZoneProgress | null
  activeTeam: ClientTeam | null
  combatMembersByKey: Map<string, ClientCombatMember>
  ownedCharacterNames: Map<string, string>
  reviveSeconds: number
  loading: boolean
  activeTeamId: string
  socketStatus: SocketStatus
  onStartCombat: () => void
  onStopCombat: () => void
  onRefreshState: () => void
}

export function CombatWorkspace({
  hud,
  combat,
  skillEvents,
  memberLabels,
  memberImages,
  skillByCharacterKey,
  topZone,
  activeTeam,
  combatMembersByKey,
  ownedCharacterNames,
  reviveSeconds,
  loading,
  activeTeamId,
  socketStatus,
  onStartCombat,
  onStopCombat,
  onRefreshState,
}: CombatWorkspaceProps) {
  const [skillCooldownDisplay, setSkillCooldownDisplay] = useState<Record<string, number>>({})
  const previousServerCooldownRef = useRef<Record<string, number>>({})
  const latestServerCooldownRef = useRef<Record<string, number>>({})
  const liveCombatMembers = combat?.members ?? EMPTY_COMBAT_MEMBERS
  const serverCooldownByKey = useMemo(
    () =>
      Object.fromEntries(
        liveCombatMembers.map((member) => [
          member.characterKey,
          member.skillCooldownRemainingMillis ?? 0,
        ]),
      ),
    [liveCombatMembers],
  )

  useEffect(() => {
    latestServerCooldownRef.current = serverCooldownByKey
  }, [serverCooldownByKey])

  useEffect(() => {
    setSkillCooldownDisplay((current) => {
      const next: Record<string, number> = {}
      let changed = false
      const previousServer = previousServerCooldownRef.current
      for (const member of liveCombatMembers) {
        const key = member.characterKey
        const serverRemaining = Math.max(0, member.skillCooldownRemainingMillis ?? 0)
        const previousServerRemaining = previousServer[key] ?? 0
        const currentRemaining = Math.max(0, current[key] ?? 0)
        // Skill use is server-authoritative: when cooldown jumps up, start/restart local timer.
        const skillWasUsed = serverRemaining > previousServerRemaining + 250
        const nextRemaining = skillWasUsed
          ? serverRemaining
          : currentRemaining > 0
            ? currentRemaining
            : serverRemaining // Local timer finished: reconcile from latest server state.
        next[key] = nextRemaining
        if (nextRemaining !== currentRemaining) {
          changed = true
        }
      }
      if (!changed) {
        for (const key of Object.keys(current)) {
          if (!(key in next)) {
            changed = true
            break
          }
        }
      }
      previousServerCooldownRef.current = Object.fromEntries(
        liveCombatMembers.map((member) => [member.characterKey, Math.max(0, member.skillCooldownRemainingMillis ?? 0)]),
      )
      return changed ? next : current
    })
  }, [liveCombatMembers])

  useEffect(() => {
    if (combat?.status !== 'FIGHTING') {
      return
    }
    const intervalId = window.setInterval(() => {
      setSkillCooldownDisplay((current) => {
        const next: Record<string, number> = {}
        let changed = false
        for (const key of Object.keys(current)) {
          const currentRemaining = Math.max(0, current[key] ?? 0)
          if (currentRemaining > 0) {
            const decremented = Math.max(0, currentRemaining - 100)
            next[key] = decremented
            if (decremented !== currentRemaining) {
              changed = true
            }
            continue
          }
          // Only validate against server after local timer reaches 0.
          const serverRemaining = Math.max(0, latestServerCooldownRef.current[key] ?? 0)
          next[key] = serverRemaining
          if (serverRemaining !== currentRemaining) {
            changed = true
          }
        }
        return changed ? next : current
      })
    }, 100)
    return () => {
      window.clearInterval(intervalId)
    }
  }, [combat?.status])

  return (
    <>
      <section className="workspace-main panel">
        <section className="workspace-section">
          <SectionHeader eyebrow="Encounter" title={hud.title} aside={<div className="header-chip">{hud.subtitle}</div>} />

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
            {socketStatus !== 'connected' ? (
              <FeedbackState
                title="Realtime combat is unavailable"
                detail="Reconnect to the game server to receive live combat updates and issue combat commands."
                tone="warning"
                actions={
                  <ActionButton onClick={onRefreshState} slim>
                    Refresh state
                  </ActionButton>
                }
              />
            ) : combat == null ? (
              <FeedbackState
                title="No active combat"
                detail="Start combat after activating a team to begin receiving live encounter updates."
                tone="neutral"
              />
            ) : (
              <CombatViewport snapshot={combat} skillEvents={skillEvents} memberLabels={memberLabels} memberImages={memberImages} />
            )}
          </div>
        </section>
      </section>

      <aside className="workspace-context panel">
        <section className="workspace-section">
          <SectionHeader eyebrow="Context" title="Combat Intel" />

          <div className="stack-panel">
            <div className="button-row workspace-actions context-actions">
              <ActionButton disabled={loading || !activeTeamId} onClick={onStartCombat} variant="primary">
                Start Combat
              </ActionButton>
              <ActionButton disabled={loading || combat == null} onClick={onStopCombat}>
                Stop Combat
              </ActionButton>
              <ActionButton disabled={loading} onClick={onRefreshState}>
                Refresh State
              </ActionButton>
            </div>
            {loading ? <FeedbackState title="Updating combat state" detail="Waiting for the latest player and combat data to settle." tone="neutral" /> : null}
            <SurfaceCard>
              <span className="label">Enemy</span>
              <strong>{combat?.enemyName ?? 'Awaiting spawn'}</strong>
              <p>
                {combat?.status ?? 'Idle'} state
                {combat?.status === 'DOWN' ? ` · revives in ${reviveSeconds}s` : ` · retaliates for ${combat?.enemyAttack.toFixed(1) ?? '0.0'}`}
              </p>
            </SurfaceCard>
            <SurfaceCard as="section">
              <span className="label">Active Team</span>
              <strong>{(activeTeam?.occupiedSlots ?? 0)}/3 ready</strong>
              <div className="combat-member-list">
                {(activeTeam?.slots ?? emptySlots()).map((slot) => {
                  if (slot.characterKey == null) {
                    return (
                      <SurfaceCard className="combat-member-card is-empty" key={slot.position}>
                        <span className="label">Slot {slot.position}</span>
                        <strong>Empty slot</strong>
                        <p>Assign a character in the Team view to use this slot.</p>
                      </SurfaceCard>
                    )
                  }

                  const member = combatMembersByKey.get(slot.characterKey)
                  return (
                    <SurfaceCard className="combat-member-card" key={slot.position}>
                      <span className="label">Slot {slot.position}</span>
                      <strong>{ownedCharacterNames.get(slot.characterKey) ?? slot.characterKey}</strong>
                      {member ? (
                        <>
                          <p>HP {member.hpLabel}</p>
                          <p>ATK {member.attack.toFixed(1)} · HIT {member.hit.toFixed(1)}</p>
                          <p>{skillLabel(slot.characterKey, skillByCharacterKey, skillCooldownDisplay, serverCooldownByKey)}</p>
                          <p>{member.alive ? 'Alive' : 'Down'}</p>
                        </>
                      ) : (
                        <p>Awaiting combat sync.</p>
                      )}
                    </SurfaceCard>
                  )
                })}
              </div>
            </SurfaceCard>
          </div>
        </section>
      </aside>
    </>
  )
}

function skillLabel(
  characterKey: string,
  skillByCharacterKey: Map<string, { name: string; cooldownMillis: number }>,
  skillCooldownDisplay: Record<string, number>,
  serverCooldownByKey: Record<string, number>,
) {
  const skill = skillByCharacterKey.get(characterKey)
  if (!skill) {
    return 'Skill N/A'
  }
  const localRemaining = skillCooldownDisplay[characterKey] ?? 0
  const serverRemaining = serverCooldownByKey[characterKey] ?? 0
  const remaining = localRemaining > 0 ? localRemaining : serverRemaining
  if (remaining <= 0) {
    return `${skill.name}: Ready`
  }
  return `${skill.name}: ${Math.ceil(remaining / 100) / 10}s`
}

function emptySlots() {
  return [
    { position: 1, characterKey: null, weaponItemId: null, armorItemId: null, accessoryItemId: null },
    { position: 2, characterKey: null, weaponItemId: null, armorItemId: null, accessoryItemId: null },
    { position: 3, characterKey: null, weaponItemId: null, armorItemId: null, accessoryItemId: null },
  ]
}
