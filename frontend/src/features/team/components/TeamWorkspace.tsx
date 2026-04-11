import {
  useEffect,
  useMemo,
  useState,
  type Dispatch,
  type MouseEvent as ReactMouseEvent,
  type SetStateAction,
} from 'react'
import type { EquipmentSlot } from '../../../core/types/api'
import { FeedbackState } from '../../workspace/components/FeedbackState'
import type {
  ClientCharacterTemplate,
  ClientInventoryItem,
  ClientOwnedCharacter,
  ClientTeam,
  ClientTeamSlot,
} from '../../session/model/clientModels'
import {
  describePassive,
  formatGrowth,
  growthAtLevel,
  mapCharacterDisplayModels,
  numericGrowthAtLevel,
  type CharacterDisplayModel,
} from '../../session/model/characterDisplay'
import { InventoryTile } from '../../inventory/components/InventoryTile'
import { equipmentSlotLabel, itemTypeAbbreviation } from '../../inventory/model/itemPresentation'
import { CharacterRosterTile } from '../../roster/components/CharacterRosterTile'
import { ActionButton } from '../../../shared/ui/ActionButton'
import { CollectionTile } from '../../../shared/ui/CollectionTile'
import { HoverPreviewCard } from '../../../shared/ui/HoverPreviewCard'
import { SectionHeader } from '../../../shared/ui/SectionHeader'
import { SurfaceCard } from '../../../shared/ui/SurfaceCard'
import { createHoverTileHandlers, hoverPreviewStyle } from '../../../shared/ui/hoverPreview'
import { useDelayedHover } from '../../../shared/ui/useDelayedHover'
import { cloneSlots, currentEquipmentForSlot, emptySlots, orderedCharactersForSlot, orderedItemsForSlot, setEquipmentOnSlot, slotsEqual } from './teamLoadout'
import './team.css'

interface TeamWorkspaceProps {
  teams: ClientTeam[]
  selectedTeam: ClientTeam | null
  activeTeamId: string
  inventory: ClientInventoryItem[]
  ownedCharacters: ClientOwnedCharacter[]
  templates: ClientCharacterTemplate[]
  ownedCharacterNames: Map<string, string>
  activeTeam: ClientTeam | null
  loading: boolean
  onTeamChange: (teamId: string) => void
  onSaveTeam: (teamId: string, slots: ClientTeamSlot[]) => Promise<void> | void
  onActivateTeam: (teamId: string) => void
}

type SelectionState =
  | { type: 'character'; position: number }
  | { type: 'equipment'; position: number; equipmentSlot: EquipmentSlot }
  | null

type HoverTarget = { type: 'character'; key: string } | { type: 'item'; id: string }

interface TeamSelectionHoverController {
  clear: () => void
  clearIfTarget: (target: HoverTarget) => void
  queueFromPointer: (target: HoverTarget, event: ReactMouseEvent<HTMLButtonElement>) => void
  updateFromPointer: (target: HoverTarget, event: ReactMouseEvent<HTMLButtonElement>) => void
  showFromFocus: (target: HoverTarget) => void
}

export function TeamWorkspace({
  teams,
  selectedTeam,
  activeTeamId,
  inventory,
  ownedCharacters,
  templates,
  ownedCharacterNames,
  activeTeam,
  loading,
  onTeamChange,
  onSaveTeam,
  onActivateTeam,
}: TeamWorkspaceProps) {
  const [editingTeamId, setEditingTeamId] = useState<string | null>(null)
  const [draftSlots, setDraftSlots] = useState<ClientTeamSlot[]>(emptySlots())
  const [selectionState, setSelectionState] = useState<SelectionState>(null)
  const hover = useDelayedHover<HoverTarget>({
    matches: (current, target) =>
      current.type === target.type &&
      (current.type === 'character'
        ? target.type === 'character' && current.key === target.key
        : target.type === 'item' && current.id === target.id),
  })
  const characterImages = useMemo(
    () => new Map(ownedCharacters.map((character) => [character.key, `/assets/characters/${character.key}/front.png`])),
    [ownedCharacters],
  )
  const rosterCharacters = useMemo(() => mapCharacterDisplayModels(ownedCharacters, templates), [ownedCharacters, templates])

  useEffect(() => {
    if (editingTeamId && !teams.some((team) => team.id === editingTeamId)) {
      setEditingTeamId(null)
      setSelectionState(null)
      setDraftSlots(emptySlots())
    }
  }, [editingTeamId, teams])

  const editingTeam = teams.find((team) => team.id === editingTeamId) ?? selectedTeam ?? teams[0] ?? null
  const editingTeamIndex = editingTeam ? teams.findIndex((team) => team.id === editingTeam.id) : -1
  const editingTeamLabel = editingTeamIndex >= 0 ? `Team ${editingTeamIndex + 1}` : 'Team'
  const hoverState = hover.hoverState
  const hoveredCharacter = hoverState?.type === 'character' ? rosterCharacters.find((character) => character.key === hoverState.key) ?? null : null
  const hoveredItem = hoverState?.type === 'item' ? inventory.find((item) => item.id === hoverState.id) ?? null : null
  const hasDraftChanges = editingTeam != null && !slotsEqual(draftSlots, editingTeam.slots)

  useEffect(() => {
    if (editingTeamId == null || editingTeam == null) {
      return
    }
    setDraftSlots(cloneSlots(editingTeam.slots))
  }, [editingTeamId, editingTeam?.id])

  useEffect(() => {
    if (selectionState != null) {
      return
    }
    hover.clear()
  }, [selectionState])

  if (editingTeamId == null) {
    return (
      <>
        <section className="workspace-main panel team-shell">
          <section className="workspace-section team-overview-section">
            <SectionHeader eyebrow="Formation" title="Team List" />

            <div className="team-overview-grid">
              {teams.map((team, index) => (
                <article
                  aria-label={`Edit team ${index + 1}`}
                  className={`team-overview-card${team.id === activeTeamId ? ' is-active' : ''}`}
                  key={team.id}
                  onClick={() => {
                    onTeamChange(team.id)
                    setEditingTeamId(team.id)
                    setSelectionState(null)
                    setDraftSlots(cloneSlots(team.slots))
                  }}
                  onKeyDown={(event) => {
                    if (event.key !== 'Enter' && event.key !== ' ') {
                      return
                    }
                    event.preventDefault()
                    onTeamChange(team.id)
                    setEditingTeamId(team.id)
                    setSelectionState(null)
                    setDraftSlots(cloneSlots(team.slots))
                  }}
                  role="button"
                  tabIndex={0}
                >
                  <div className="team-overview-heading">
                    <div className="team-overview-heading-top">
                      <span className="label team-overview-label">Team {index + 1}</span>
                      <strong>{team.id === activeTeamId ? `${team.shortLabel} · Active` : team.shortLabel}</strong>
                    </div>
                  </div>

                  <div className="team-slot-summary-list">
                    {team.slots.map((slot) => {
                      const weapon = inventory.find((item) => item.id === slot.weaponItemId) ?? null
                      const armor = inventory.find((item) => item.id === slot.armorItemId) ?? null
                      const accessory = inventory.find((item) => item.id === slot.accessoryItemId) ?? null
                      const portrait = slot.characterKey ? characterImages.get(slot.characterKey) : null
                      return (
                        <div aria-hidden="true" className="team-slot-summary-card" key={slot.position}>
                          <span aria-hidden={!slot.characterKey} className={`label team-slot-summary-label${slot.characterKey ? '' : ' is-placeholder'}`}>
                            {slot.characterKey ? ownedCharacterNames.get(slot.characterKey) ?? slot.characterKey : 'Empty'}
                          </span>
                          <div className="team-slot-summary-portrait">
                            {portrait ? <img alt="" loading="lazy" src={portrait} /> : <span>Empty</span>}
                          </div>
                          <div className="team-gear-inline" aria-label="Equipment summary">
                            <span aria-label={weapon ? `Weapon equipped: ${weapon.itemDisplayName}` : 'Weapon empty'} className={weapon ? 'is-filled' : ''}>
                              <span className="team-gear-icon team-gear-icon--weapon" />
                            </span>
                            <span aria-label={armor ? `Armor equipped: ${armor.itemDisplayName}` : 'Armor empty'} className={armor ? 'is-filled' : ''}>
                              <span className="team-gear-icon team-gear-icon--armor" />
                            </span>
                            <span
                              aria-label={accessory ? `Accessory equipped: ${accessory.itemDisplayName}` : 'Accessory empty'}
                              className={accessory ? 'is-filled' : ''}
                            >
                              <span className="team-gear-icon team-gear-icon--accessory" />
                            </span>
                          </div>
                        </div>
                      )
                    })}
                  </div>

                  <div
                    className="team-overview-actions"
                    onClick={(event) => event.stopPropagation()}
                    onKeyDown={(event) => event.stopPropagation()}
                  >
                    {team.id === activeTeamId ? <span className="team-status-chip is-active">Active</span> : null}
                    <ActionButton disabled={loading || team.id === activeTeamId} onClick={() => onActivateTeam(team.id)} slim>
                      {team.id === activeTeamId ? 'Active' : 'Set Active'}
                    </ActionButton>
                  </div>
                </article>
              ))}
            </div>
          </section>
        </section>

        <aside className="workspace-context panel">
          <section className="workspace-section">
            <SectionHeader eyebrow="Context" title="Team Notes" />

            <div className="stack-panel">
              <SurfaceCard>
                <span className="label">Active Team</span>
                <strong>{activeTeam?.id ? activeTeam.shortLabel : 'None'}</strong>
                <p>Select a team card to open the visual editor.</p>
              </SurfaceCard>
              <SurfaceCard>
                <span className="label">Slots</span>
                <strong>3 per team</strong>
                <p>Every team card shows all slots, including empty positions and missing equipment.</p>
              </SurfaceCard>
            </div>
          </section>
        </aside>
      </>
    )
  }

  if (editingTeam == null) {
    return null
  }

  return (
    <>
      <section className="workspace-main panel team-shell">
        <section className="workspace-section team-editor-section">
          {selectionState ? (
            <SelectionView
              editingTeam={editingTeam}
              editingTeamLabel={editingTeamLabel}
              hover={hover}
              inventory={inventory}
              ownedCharacters={ownedCharacters}
              selectionState={selectionState}
              setSelectionState={setSelectionState}
              setDraftSlots={setDraftSlots}
              slots={draftSlots}
            />
          ) : (
            <>
              <SectionHeader
                eyebrow="Formation"
                title="Team Editor"
                aside={
                  <div className="team-editor-actions">
                    <ActionButton
                      onClick={() => {
                        setEditingTeamId(null)
                        setSelectionState(null)
                        setDraftSlots(emptySlots())
                      }}
                      slim
                      variant="ghost"
                    >
                      Back
                    </ActionButton>
                  </div>
                }
              />

              <SurfaceCard className="team-editor-summary-card">
                <span className="label">Editing</span>
                <strong>
                  {editingTeamLabel} <span className="team-editor-summary-id">{editingTeam.shortLabel}</span>
                </strong>
              </SurfaceCard>

              <div className="team-editor-slot-grid">
                {draftSlots.map((slot) => {
                  const slotCharacter = slot.characterKey ? rosterCharacters.find((character) => character.key === slot.characterKey) ?? null : null
                  const combinedStats = slotCharacter ? combinedSlotStats(slotCharacter, inventory, slot) : []
                  return (
                  <SurfaceCard className="team-editor-slot-card" key={slot.position}>
                    <div className="team-editor-slot-heading">
                      <span className="label">Slot {slot.position}</span>
                    </div>

                    <CollectionTile
                      ariaLabel={`Choose character for slot ${slot.position}`}
                      className="team-character-slot-button"
                      name={slot.characterKey ? ownedCharacterNames.get(slot.characterKey) ?? slot.characterKey : 'Empty'}
                      onClick={() => setSelectionState({ type: 'character', position: slot.position })}
                      portrait={
                        <>
                          {slot.characterKey ? (
                            <img
                              alt=""
                              className="character-tile__portrait"
                              loading="lazy"
                              src={characterImages.get(slot.characterKey) ?? undefined}
                            />
                          ) : (
                            <div aria-hidden="true" className="character-tile__fallback team-character-slot__fallback">
                              Empty
                            </div>
                          )}
                          <span className="character-tile__badge">Slot {slot.position}</span>
                        </>
                      }
                      copyAside={<span className="team-slot-copy">Tap to choose a character</span>}
                    />

                    <div className="team-editor-equipment-row">
                      <EquipmentSlotButton
                        disabled={loading || slot.characterKey == null}
                        item={inventory.find((item) => item.id === slot.weaponItemId) ?? null}
                        onClick={() => setSelectionState({ type: 'equipment', position: slot.position, equipmentSlot: 'WEAPON' })}
                        position={slot.position}
                        slot="WEAPON"
                      />
                      <EquipmentSlotButton
                        disabled={loading || slot.characterKey == null}
                        item={inventory.find((item) => item.id === slot.armorItemId) ?? null}
                        onClick={() => setSelectionState({ type: 'equipment', position: slot.position, equipmentSlot: 'ARMOR' })}
                        position={slot.position}
                        slot="ARMOR"
                      />
                      <EquipmentSlotButton
                        disabled={loading || slot.characterKey == null}
                        item={inventory.find((item) => item.id === slot.accessoryItemId) ?? null}
                        onClick={() => setSelectionState({ type: 'equipment', position: slot.position, equipmentSlot: 'ACCESSORY' })}
                        position={slot.position}
                        slot="ACCESSORY"
                      />
                    </div>
                    {slotCharacter ? (
                      <div className="team-editor-stat-grid" aria-label={`Combined stats for slot ${slot.position}`}>
                        {combinedStats.map((stat) => (
                          <div className="team-editor-stat-chip" key={stat.label}>
                            <span className="label">{stat.label}</span>
                            <strong>{stat.value}</strong>
                          </div>
                        ))}
                      </div>
                    ) : null}
                  </SurfaceCard>
                )})}
              </div>

              <div className="button-row workspace-actions">
                <ActionButton
                  disabled={loading || !hasDraftChanges}
                  onClick={() => {
                    if (!editingTeam) {
                      return
                    }
                    void Promise.resolve(onSaveTeam(editingTeam.id, draftSlots)).then(() => {
                      setEditingTeamId(null)
                      setSelectionState(null)
                      setDraftSlots(emptySlots())
                    })
                  }}
                >
                  Save Team
                </ActionButton>
              </div>
            </>
          )}
        </section>
      </section>

      <aside className="workspace-context panel">
        <section className="workspace-section">
          <SectionHeader eyebrow="Context" title="Team Notes" />

          <div className="stack-panel">
            <SurfaceCard>
              <span className="label">Active Team</span>
              <strong>{activeTeam?.id ? activeTeam.shortLabel : 'None'}</strong>
              <p>{editingTeam.id === activeTeam?.id ? 'You are editing the active team.' : 'You are editing a reserve team.'}</p>
            </SurfaceCard>
            <SurfaceCard>
              <span className="label">Loadout</span>
              <strong>{editingTeamLabel}</strong>
              <p>Character and equipment selection both use the same visual hover and focus language as the roster and inventory views.</p>
            </SurfaceCard>
          </div>
        </section>
      </aside>

      {hoveredCharacter && hoverState?.type === 'character' ? (
        <HoverPreviewCard
          className="team-hover-card"
          headerClassName="team-hover-header"
          style={hoverPreviewStyle(hoverState)}
          title={hoveredCharacter.name}
          badge={`Lv ${hoveredCharacter.level}`}
        >
          <p className="muted">{hoveredCharacter.key}</p>
          <div className="team-hover-tags">
            {hoveredCharacter.tags.length > 0 ? (
              hoveredCharacter.tags.map((tag) => (
                <span className="team-hover-tag" key={tag}>
                  {tag}
                </span>
              ))
            ) : (
              <span className="muted">No tags</span>
            )}
          </div>
          <p>
            STR {growthAtLevel(hoveredCharacter.strength, hoveredCharacter.level)} · AGI {growthAtLevel(hoveredCharacter.agility, hoveredCharacter.level)} · VIT{' '}
            {growthAtLevel(hoveredCharacter.vitality, hoveredCharacter.level)}
          </p>
          <p className="muted">
            Skill: {hoveredCharacter.skill?.name ?? 'None'} | Passive: {hoveredCharacter.passive?.name ?? 'None'}
          </p>
          {hoveredCharacter.passive ? <p className="muted">{describePassive(hoveredCharacter.passive)}</p> : null}
        </HoverPreviewCard>
      ) : null}

      {hoveredItem && hoverState?.type === 'item' ? (
        <HoverPreviewCard
          className="team-hover-card"
          headerClassName="team-hover-header"
          style={hoverPreviewStyle(hoverState)}
          title={hoveredItem.itemDisplayName}
          badge={`Lv ${hoveredItem.itemLevel}`}
        >
          <p className="muted">
            {hoveredItem.rarity} | {hoveredItem.itemType}
          </p>
          <p>
            {hoveredItem.itemBaseStat.type} <strong>{hoveredItem.itemBaseStat.value}</strong>
          </p>
          <p className="muted">{hoveredItem.assignmentLabel}</p>
          {hoveredItem.subStats.length > 0 ? <p>{hoveredItem.subStats.map((stat) => `${stat.type} ${stat.value}`).join(' · ')}</p> : null}
        </HoverPreviewCard>
      ) : null}
    </>
  )
}

interface SelectionViewProps {
  editingTeam: ClientTeam
  editingTeamLabel: string
  slots: ClientTeamSlot[]
  inventory: ClientInventoryItem[]
  ownedCharacters: ClientOwnedCharacter[]
  selectionState: Exclude<SelectionState, null>
  hover: TeamSelectionHoverController
  setSelectionState: Dispatch<SetStateAction<SelectionState>>
  setDraftSlots: Dispatch<SetStateAction<ClientTeamSlot[]>>
}

function SelectionView({
  editingTeam,
  editingTeamLabel,
  slots,
  inventory,
  ownedCharacters,
  selectionState,
  hover,
  setSelectionState,
  setDraftSlots,
}: SelectionViewProps) {
  const slot = slots.find((entry) => entry.position === selectionState.position) ?? null
  const closeSelection = () => {
    hover.clear()
    setSelectionState(null)
  }

  if (slot == null) {
    return null
  }

  if (selectionState.type === 'character') {
    const options = orderedCharactersForSlot(slot.position, slots, ownedCharacters)
    return (
      <>
        <SectionHeader
          eyebrow="Formation"
          title="Select Character"
          aside={
            <ActionButton onClick={closeSelection} slim variant="ghost">
              Back
            </ActionButton>
          }
        />

        <SurfaceCard className="team-selection-summary-card">
          <span className="label">Editing</span>
          <strong>
            {editingTeamLabel} · Slot {slot.position}
          </strong>
          <p>{slot.characterKey ? 'Current character is pinned first in the list.' : 'Available characters are ordered alphabetically.'}</p>
        </SurfaceCard>

        {options.length === 0 ? (
          <FeedbackState detail="Unlock a character first, then return here to assign them." title="No characters available" tone="neutral" />
        ) : (
          <div className="card-grid team-selection-grid">
            {options.map((character) => (
              <CharacterRosterTile
                badge={`Lv ${character.level}`}
                image={`/assets/characters/${character.key}/front.png`}
                key={character.key}
                name={character.name}
                onClick={() => {
                  setDraftSlots((current) =>
                    current.map((entry) => (entry.position === slot.position ? { ...entry, characterKey: character.key } : entry)),
                  )
                  closeSelection()
                }}
                {...createHoverTileHandlers(hover, { type: 'character', key: character.key })}
                selected={slot.characterKey === character.key}
              />
            ))}
          </div>
        )}
      </>
    )
  }

  const currentItem = currentEquipmentForSlot(inventory, slot, selectionState.equipmentSlot)
  const options = orderedItemsForSlot(inventory, slots, editingTeam.id, slot.position, selectionState.equipmentSlot, currentItem)
  const slotLabel = equipmentSlotLabel(selectionState.equipmentSlot)
  return (
    <>
      <SectionHeader
        eyebrow="Formation"
        title={`Select ${slotLabel}`}
        aside={
          <ActionButton onClick={closeSelection} slim variant="ghost">
            Back
          </ActionButton>
        }
      />

      <SurfaceCard className="team-selection-summary-card">
        <span className="label">Editing</span>
        <strong>
          {editingTeamLabel} · Slot {slot.position}
        </strong>
        <p>Empty is always listed first, then the current item if present, then the remaining {slotLabel.toLowerCase()} items by level.</p>
      </SurfaceCard>

      <div className="card-grid team-selection-grid">
        <CollectionTile
          ariaLabel={`Leave ${slotLabel.toLowerCase()} empty for slot ${slot.position}`}
          className="item-tile"
          name="Empty"
          onClick={() => {
            setDraftSlots((current) => current.map((entry) => setEquipmentOnSlot(entry, slot.position, selectionState.equipmentSlot, null)))
            closeSelection()
          }}
          portrait={
            <div aria-hidden="true" className={`item-tile__placeholder item-tile__placeholder--${selectionState.equipmentSlot.toLowerCase()} team-empty-item-tile`}>
              <span>Empty</span>
            </div>
          }
          selected={currentItem == null}
          copyAside={<span className="team-slot-copy">Unequip this slot</span>}
        />

        {options.map((item) => (
          <InventoryTile
            equipped={item.equippedTeamId != null}
            key={item.id}
            level={item.itemLevel}
            name={item.itemDisplayName}
            onClick={() => {
              setDraftSlots((current) => current.map((entry) => setEquipmentOnSlot(entry, slot.position, selectionState.equipmentSlot, item.id)))
              closeSelection()
            }}
            {...createHoverTileHandlers(hover, { type: 'item', id: item.id })}
            rarity={item.rarity}
            selected={currentItem?.id === item.id}
            type={item.itemType}
          />
        ))}
      </div>
    </>
  )
}

interface EquipmentSlotButtonProps {
  slot: EquipmentSlot
  position: number
  item: ClientInventoryItem | null
  disabled: boolean
  onClick: () => void
}

interface DisplayStat {
  label: string
  value: string
}

function EquipmentSlotButton({ slot, position, item, disabled, onClick }: EquipmentSlotButtonProps) {
  const label = equipmentSlotLabel(slot)
  return (
    <CollectionTile
      ariaLabel={`Choose ${label.toLowerCase()} for slot ${position}`}
      className={`item-tile team-equipment-slot-button${item ? ' is-equipped' : ''}${disabled ? ' is-disabled' : ''}`}
      name={label}
      onClick={disabled ? undefined : onClick}
      portrait={
        <>
          <div aria-hidden="true" className={`item-tile__placeholder item-tile__placeholder--${slot.toLowerCase()}`}>
            <span>{item ? itemTypeAbbreviation(slot) : 'Empty'}</span>
          </div>
          {item ? <span className="item-tile__badge">Lv {item.itemLevel}</span> : null}
        </>
      }
      copyAside={<span className="team-slot-copy">{disabled ? 'Assign a character first' : item?.itemDisplayName ?? 'Empty'}</span>}
      selected={item != null}
    />
  )
}

function combinedSlotStats(character: CharacterDisplayModel, inventory: ClientInventoryItem[], slot: ClientTeamSlot): DisplayStat[] {
  const totals = new Map<string, number>([
    ['STR', numericGrowthAtLevel(character.strength, character.level)],
    ['AGI', numericGrowthAtLevel(character.agility, character.level)],
    ['INT', numericGrowthAtLevel(character.intelligence, character.level)],
    ['WIS', numericGrowthAtLevel(character.wisdom, character.level)],
    ['VIT', numericGrowthAtLevel(character.vitality, character.level)],
  ])

  for (const itemId of [slot.weaponItemId, slot.armorItemId, slot.accessoryItemId]) {
    if (!itemId) {
      continue
    }
    const item = inventory.find((entry) => entry.id === itemId)
    if (!item) {
      continue
    }
    addStatTotal(totals, item.itemBaseStat.type, item.itemBaseStat.value)
    for (const stat of item.subStats) {
      addStatTotal(totals, stat.type, stat.value)
    }
  }

  return [...totals.entries()]
    .filter(([, value]) => value !== 0)
    .sort(([left], [right]) => statPriority(left) - statPriority(right) || left.localeCompare(right))
    .map(([label, value]) => ({ label, value: formatGrowth(value) }))
}

function addStatTotal(totals: Map<string, number>, statType: string, value: number) {
  const label = normalizedStatLabel(statType)
  totals.set(label, (totals.get(label) ?? 0) + value)
}

function normalizedStatLabel(statType: string) {
  switch (statType.toUpperCase()) {
    case 'STRENGTH':
      return 'STR'
    case 'AGILITY':
      return 'AGI'
    case 'INTELLIGENCE':
      return 'INT'
    case 'WISDOM':
      return 'WIS'
    case 'VITALITY':
      return 'VIT'
    case 'ATTACK':
      return 'ATK'
    default:
      return statType.toUpperCase()
  }
}

function statPriority(label: string) {
  switch (label) {
    case 'STR':
      return 0
    case 'AGI':
      return 1
    case 'INT':
      return 2
    case 'WIS':
      return 3
    case 'VIT':
      return 4
    case 'ATK':
      return 5
    case 'HP':
      return 6
    case 'HIT':
      return 7
    case 'CRIT':
      return 8
    default:
      return 99
  }
}
