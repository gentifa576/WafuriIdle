import { FeedbackState } from '../../workspace/components/FeedbackState'
import type { PullResult } from '../../session/hooks/gameClientTypes'
import type { ClientPlayer } from '../../session/model/clientModels'
import { ActionButton } from '../../../shared/ui/ActionButton'
import { SectionHeader } from '../../../shared/ui/SectionHeader'
import { SurfaceCard } from '../../../shared/ui/SurfaceCard'
import './gacha.css'

interface GachaWorkspaceProps {
  player: ClientPlayer
  latestPullResult: PullResult | null
  loading: boolean
  onPullCharacter: (count: 1 | 10) => void
}

export function GachaWorkspace({ player, latestPullResult, loading, onPullCharacter }: GachaWorkspaceProps) {
  return (
    <>
      <section className="workspace-main panel">
        <section className="workspace-section">
          <SectionHeader eyebrow="Recruitment" title="Character Pull" aside={<div className="header-chip">250 gold</div>} />

          <SurfaceCard className="gacha-card">
            <p>Every loaded character currently has an even pull chance. Duplicate pulls convert into essence.</p>
            <div className="gacha-stats">
              <strong>Gold: {player.gold}</strong>
              <strong>Essence: {player.essence}</strong>
            </div>
            {player.gold < 250 ? (
              <FeedbackState
                title="Not enough gold"
                detail="Earn more gold from combat rewards before attempting another character pull."
                tone="warning"
              />
            ) : null}
            <div className="button-row">
              <ActionButton disabled={loading || player.gold < 250} onClick={() => onPullCharacter(1)} variant="primary">
                {loading ? 'Pulling...' : 'Pull x1'}
              </ActionButton>
              <ActionButton disabled={loading || player.gold < 250} onClick={() => onPullCharacter(10)} variant="secondary">
                {loading ? 'Pulling...' : 'Pull x10'}
              </ActionButton>
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
              <FeedbackState title="No pull results yet" detail="Spend gold on a pull to see unlocks and duplicate essence results here." tone="neutral" />
            )}
          </SurfaceCard>
        </section>
      </section>

      <aside className="workspace-context panel" />
    </>
  )
}
