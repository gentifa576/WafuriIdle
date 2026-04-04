import { FeedbackState } from '../../workspace/components/FeedbackState'
import { useState } from 'react'
import type { ClientInventoryItem } from '../../session/model/clientModels'
import './inventory.css'

interface InventoryWorkspaceProps {
  inventory: ClientInventoryItem[]
}

export function InventoryWorkspace({ inventory }: InventoryWorkspaceProps) {
  return (
    <>
      <section className="workspace-main panel">
        <section className="workspace-section">
          <div className="section-heading">
            <div>
              <p className="eyebrow">Storage</p>
              <h2>Inventory</h2>
            </div>
            <span className="section-count">{inventory.length}</span>
          </div>

          <div className="card-grid inventory-grid">
            {inventory.length === 0 ? (
              <FeedbackState title="Inventory is empty" detail="Loot from combat will appear here once enemies start dropping equipment." tone="neutral" />
            ) : null}
            {inventory.map((item) => <InventoryItemCard item={item} key={item.id} />)}
          </div>
        </section>
      </section>

      <aside className="workspace-context panel">
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
              <p>Use Inspect details on an item card to review its stats.</p>
            </article>
          </div>
        </section>
      </aside>
    </>
  )
}

function InventoryItemCard({ item }: { item: ClientInventoryItem }) {
  const [detailsOpen, setDetailsOpen] = useState(false)
  const detailId = `inventory-item-details-${item.id}`

  return (
    <article className="workspace-card inventory-wide-card">
      <strong>{item.itemDisplayName}</strong>
      <p>{item.equippedTeamId ? `Equipped to slot ${item.equippedPosition}` : 'In backpack'}</p>
      <button
        aria-controls={detailId}
        aria-expanded={detailsOpen}
        className="ghost-button slim"
        onClick={() => setDetailsOpen((current) => !current)}
        type="button"
      >
        {detailsOpen ? 'Hide details' : 'Inspect details'}
      </button>
      {detailsOpen ? (
        <div aria-label={`${item.itemDisplayName} details`} className="item-hover-card item-detail-panel" id={detailId} role="region">
          <strong>{item.itemDisplayName}</strong>
          <p>{item.rarity} · {item.itemType}</p>
          <p>{item.itemBaseStat.type} {item.itemBaseStat.value}</p>
          {item.subStats.length > 0 ? <p>{item.subStats.map((stat) => `${stat.type} ${stat.value}`).join(' · ')}</p> : null}
        </div>
      ) : null}
    </article>
  )
}
