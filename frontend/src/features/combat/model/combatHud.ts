import type { ClientCombat } from '../../session/model/clientModels'

export function toCombatHud(snapshot: ClientCombat | null) {
  if (!snapshot) {
    return {
      title: 'No active combat',
      subtitle: 'Start combat after activating a team.',
      enemyRatio: 0,
      teamCurrentHp: 0,
      teamMaxHp: 0,
    }
  }

  return {
    title: snapshot.enemyName,
    subtitle:
      snapshot.status === 'DOWN'
        ? `DOWN · Revive in ${reviveSecondsRemaining(snapshot.pendingReviveMillis)}s`
        : `${snapshot.status} · DPS ${snapshot.teamDps.toFixed(1)} · Retaliate ${snapshot.enemyAttack.toFixed(1)}`,
    enemyRatio: snapshot.enemyMaxHp === 0 ? 0 : snapshot.enemyHp / snapshot.enemyMaxHp,
    teamCurrentHp: snapshot.teamCurrentHp,
    teamMaxHp: snapshot.teamMaxHp,
  }
}

export function reviveSecondsRemaining(pendingReviveMillis: number) {
  return Math.max(0, Math.ceil((30000 - pendingReviveMillis) / 1000))
}
