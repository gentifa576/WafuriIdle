import type { CombatSnapshot } from '../../../core/types/api'

export function toCombatHud(snapshot: CombatSnapshot | null) {
  if (!snapshot) {
    return {
      title: 'No active combat',
      subtitle: 'Start combat after activating a team.',
      enemyRatio: 0,
    }
  }

  return {
    title: snapshot.enemyName,
    subtitle: `${snapshot.status} · DPS ${snapshot.teamDps.toFixed(1)}`,
    enemyRatio: snapshot.enemyMaxHp === 0 ? 0 : snapshot.enemyHp / snapshot.enemyMaxHp,
  }
}
