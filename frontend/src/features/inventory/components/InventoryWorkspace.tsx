import {
  useEffect,
  useMemo,
  useRef,
  useState,
  type Dispatch,
  type MouseEvent as ReactMouseEvent,
  type MutableRefObject,
  type SetStateAction,
} from 'react'
import type { ClientInventoryItem } from '../../session/model/clientModels'
import { FeedbackState } from '../../workspace/components/FeedbackState'
import { ActionButton } from '../../../shared/ui/ActionButton'
import { SectionHeader } from '../../../shared/ui/SectionHeader'
import { SurfaceCard } from '../../../shared/ui/SurfaceCard'
import { InventoryTile } from './InventoryTile'
import './inventory.css'

interface InventoryWorkspaceProps {
  inventory: ClientInventoryItem[]
}

interface HoverState {
  id: string
  x: number
  y: number
}

const HOVER_DELAY_MS = 400
const ITEM_TYPE_PRIORITY: Record<string, number> = {
  WEAPON: 0,
  ARMOR: 1,
  ACCESSORY: 2,
}

export function InventoryWorkspace({ inventory }: InventoryWorkspaceProps) {
  const [selectedItemId, setSelectedItemId] = useState<string | null>(null)
  const [hoverState, setHoverState] = useState<HoverState | null>(null)
  const hoverTimeoutRef = useRef<number | null>(null)
  const sortedInventory = useMemo(() => [...inventory].sort(compareInventoryItems), [inventory])
  const selectedItem = sortedInventory.find((item) => item.id === selectedItemId) ?? null
  const hoveredItem = sortedInventory.find((item) => item.id === hoverState?.id) ?? null
  const equippedCount = sortedInventory.filter((item) => isEquipped(item)).length

  useEffect(() => {
    if (selectedItemId && !sortedInventory.some((item) => item.id === selectedItemId)) {
      setSelectedItemId(null)
    }
  }, [selectedItemId, sortedInventory])

  useEffect(() => () => clearHoverTimer(hoverTimeoutRef), [])

  return (
    <>
      <section className="workspace-main panel inventory-shell">
        <section className="workspace-section inventory-grid-section">
          {selectedItem ? (
            <>
              <SectionHeader
                eyebrow="Storage"
                title={selectedItem.itemDisplayName}
                aside={
                  <ActionButton onClick={() => setSelectedItemId(null)} slim variant="ghost">
                    Back
                  </ActionButton>
                }
              />

              <div className="inventory-detail-layout">
                <div className="inventory-detail-stage">
                  <div className="inventory-detail-header">
                    <InventoryTile
                      compact
                      equipped={isEquipped(selectedItem)}
                      level={selectedItem.itemLevel}
                      name={selectedItem.itemDisplayName}
                      rarity={selectedItem.rarity}
                      selected
                      type={selectedItem.itemType}
                    />
                  </div>

                  <SurfaceCard className="inventory-detail-panel">
                    <div className="inventory-detail-summary">
                      <span className="label">Item Details</span>
                      <h3>{selectedItem.itemDisplayName}</h3>
                      <p>
                        Level {selectedItem.itemLevel} {selectedItem.rarity.toLowerCase()} {selectedItem.itemType.toLowerCase()}
                      </p>
                      <p className="muted">{selectedItem.assignmentLabel}</p>
                    </div>

                    <div className="inventory-stat-grid">
                      <div className="inventory-stat">
                        <span className="label">Base Stat</span>
                        <strong>{selectedItem.itemBaseStat.value}</strong>
                        <small className="muted">{selectedItem.itemBaseStat.type}</small>
                      </div>
                      <div className="inventory-stat">
                        <span className="label">Upgrade</span>
                        <strong>+{selectedItem.upgrade}</strong>
                        <small className="muted">Current enhancement</small>
                      </div>
                      <div className="inventory-stat">
                        <span className="label">Status</span>
                        <strong>{isEquipped(selectedItem) ? 'Equipped' : 'Backpack'}</strong>
                        <small className="muted">{selectedItem.itemType}</small>
                      </div>
                    </div>

                    <div className="inventory-detail-meta">
                      <SurfaceCard>
                        <span className="label">Sub Stats</span>
                        <h3>{selectedItem.subStats.length > 0 ? `${selectedItem.subStats.length} rolled` : 'No sub stats'}</h3>
                        <div className="inventory-substats">
                          {selectedItem.subStats.length > 0 ? (
                            selectedItem.subStats.map((stat) => (
                              <p key={`${stat.type}-${stat.value}`}>
                                {stat.type} <strong>{stat.value}</strong>
                              </p>
                            ))
                          ) : (
                            <p className="muted">This item currently has no bonus rolls.</p>
                          )}
                        </div>
                      </SurfaceCard>

                      <SurfaceCard>
                        <span className="label">Assignment</span>
                        <h3>{selectedItem.assignmentLabel}</h3>
                        <p className="muted">
                          {isEquipped(selectedItem)
                            ? `Assigned to slot ${selectedItem.equippedPosition}.`
                            : 'Available to equip from the Team view.'}
                        </p>
                      </SurfaceCard>
                    </div>
                  </SurfaceCard>
                </div>
              </div>
            </>
          ) : (
            <>
              <SectionHeader eyebrow="Storage" title="Inventory" />

              <div className="inventory-scroll-body">
                {sortedInventory.length === 0 ? (
                  <FeedbackState title="Inventory is empty" detail="Loot from combat will appear here once enemies start dropping equipment." tone="neutral" />
                ) : (
                  <div className="card-grid inventory-grid">
                    {sortedInventory.map((item) => (
                      <InventoryTile
                        equipped={isEquipped(item)}
                        key={item.id}
                        level={item.itemLevel}
                        name={item.itemDisplayName}
                        onBlur={() => setHoverState((current) => (current?.id === item.id ? null : current))}
                        onClick={() => {
                          clearHoverTimer(hoverTimeoutRef)
                          setHoverState(null)
                          setSelectedItemId(item.id)
                        }}
                        onFocus={() => setHoverState({ id: item.id, x: window.innerWidth / 2, y: 176 })}
                        onMouseEnter={(event) => queueHover(item.id, event, hoverTimeoutRef, setHoverState)}
                        onMouseLeave={() => {
                          clearHoverTimer(hoverTimeoutRef)
                          setHoverState((current) => (current?.id === item.id ? null : current))
                        }}
                        onMouseMove={(event) => updateHoverPosition(item.id, event, setHoverState)}
                        rarity={item.rarity}
                        type={item.itemType}
                      />
                    ))}
                  </div>
                )}
              </div>
            </>
          )}
        </section>
      </section>

      <aside className="workspace-context panel">
        <section className="workspace-section">
          <SectionHeader eyebrow="Context" title="Inventory Notes" />

          <div className="stack-panel">
            <SurfaceCard>
              <div className="inventory-side-stat">
                <span className="label">Stored</span>
                <strong>{sortedInventory.length}</strong>
              </div>
              <p>{selectedItem ? `${selectedItem.itemDisplayName} is in focus.` : 'Select a tile to inspect the full item sheet.'}</p>
            </SurfaceCard>
            <SurfaceCard>
              <div className="inventory-side-stat">
                <span className="label">Equipped</span>
                <strong>{equippedCount}</strong>
              </div>
              <p>{sortedInventory.length - equippedCount} items are currently available in the backpack.</p>
            </SurfaceCard>
          </div>
        </section>
      </aside>

      {hoveredItem ? (
        <div
          aria-hidden="true"
          className="inventory-hover-card"
          style={{
            left: `${Math.min(hoverState!.x + 18, window.innerWidth - 340)}px`,
            top: `${Math.min(hoverState!.y + 18, window.innerHeight - 280)}px`,
          }}
        >
          <div className="inventory-hover-header">
            <div>
              <span className="label">Preview</span>
              <h3>{hoveredItem.itemDisplayName}</h3>
            </div>
            <span className="header-chip">Lv {hoveredItem.itemLevel}</span>
          </div>
          <p className="muted">
            {hoveredItem.rarity} | {hoveredItem.itemType}
          </p>
          <p>
            {hoveredItem.itemBaseStat.type} <strong>{hoveredItem.itemBaseStat.value}</strong>
          </p>
          <p className="muted">{hoveredItem.assignmentLabel}</p>
          {hoveredItem.subStats.length > 0 ? <p>{hoveredItem.subStats.map((stat) => `${stat.type} ${stat.value}`).join(' · ')}</p> : null}
        </div>
      ) : null}
    </>
  )
}

function compareInventoryItems(left: ClientInventoryItem, right: ClientInventoryItem) {
  const equippedDelta = Number(isEquipped(right)) - Number(isEquipped(left))
  if (equippedDelta !== 0) {
    return equippedDelta
  }

  const levelDelta = right.itemLevel - left.itemLevel
  if (levelDelta !== 0) {
    return levelDelta
  }

  const typeDelta = (ITEM_TYPE_PRIORITY[left.itemType] ?? 99) - (ITEM_TYPE_PRIORITY[right.itemType] ?? 99)
  if (typeDelta !== 0) {
    return typeDelta
  }

  return left.itemDisplayName.localeCompare(right.itemDisplayName)
}

function isEquipped(item: ClientInventoryItem) {
  return item.equippedTeamId != null
}

function queueHover(
  id: string,
  event: ReactMouseEvent<HTMLButtonElement>,
  hoverTimeoutRef: MutableRefObject<number | null>,
  setHoverState: Dispatch<SetStateAction<HoverState | null>>,
) {
  clearHoverTimer(hoverTimeoutRef)
  const { clientX, clientY } = event
  hoverTimeoutRef.current = window.setTimeout(() => {
    setHoverState({ id, x: clientX, y: clientY })
  }, HOVER_DELAY_MS)
}

function updateHoverPosition(
  id: string,
  event: ReactMouseEvent<HTMLButtonElement>,
  setHoverState: Dispatch<SetStateAction<HoverState | null>>,
) {
  setHoverState((current) => (current?.id === id ? { id, x: event.clientX, y: event.clientY } : current))
}

function clearHoverTimer(hoverTimeoutRef: MutableRefObject<number | null>) {
  if (hoverTimeoutRef.current != null) {
    window.clearTimeout(hoverTimeoutRef.current)
    hoverTimeoutRef.current = null
  }
}
