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
  ClientCombatConditionDefinition,
  ClientInventoryItem,
  ClientOwnedCharacter,
  ClientPassiveDefinition,
  ClientStatGrowth,
  ClientTeam,
  ClientTeamSlot,
} from '../../session/model/clientModels'
import { InventoryTile } from '../../inventory/components/InventoryTile'
import { CharacterRosterTile } from '../../roster/components/CharacterRosterTile'
import { ActionButton } from '../../../shared/ui/ActionButton'
import { CollectionTile } from '../../../shared/ui/CollectionTile'
import { SectionHeader } from '../../../shared/ui/SectionHeader'
import { SurfaceCard } from '../../../shared/ui/SurfaceCard'
import { useDelayedHover } from '../../../shared/ui/useDelayedHover'
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
  const rosterCharacters = useMemo(() => mapRosterCharacters(ownedCharacters, templates), [ownedCharacters, templates])

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
        <div
          aria-hidden="true"
          className="team-hover-card"
          style={{
            left: `${Math.min(hoverState.x + 18, window.innerWidth - 340)}px`,
            top: `${Math.min(hoverState.y + 18, window.innerHeight - 280)}px`,
          }}
        >
          <div className="team-hover-header">
            <div>
              <span className="label">Preview</span>
              <h3>{hoveredCharacter.name}</h3>
            </div>
            <span className="header-chip">Lv {hoveredCharacter.level}</span>
          </div>
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
        </div>
      ) : null}

      {hoveredItem && hoverState?.type === 'item' ? (
        <div
          aria-hidden="true"
          className="team-hover-card"
          style={{
            left: `${Math.min(hoverState.x + 18, window.innerWidth - 340)}px`,
            top: `${Math.min(hoverState.y + 18, window.innerHeight - 280)}px`,
          }}
        >
          <div className="team-hover-header">
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
                onBlur={() => hover.clearIfTarget({ type: 'character', key: character.key })}
                onClick={() => {
                  setDraftSlots((current) =>
                    current.map((entry) => (entry.position === slot.position ? { ...entry, characterKey: character.key } : entry)),
                  )
                  closeSelection()
                }}
                onFocus={() => hover.showFromFocus({ type: 'character', key: character.key })}
                onMouseEnter={(event) => hover.queueFromPointer({ type: 'character', key: character.key }, event)}
                onMouseLeave={() => hover.clearIfTarget({ type: 'character', key: character.key })}
                onMouseMove={(event) => hover.updateFromPointer({ type: 'character', key: character.key }, event)}
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
            onBlur={() => hover.clearIfTarget({ type: 'item', id: item.id })}
            onClick={() => {
              setDraftSlots((current) => current.map((entry) => setEquipmentOnSlot(entry, slot.position, selectionState.equipmentSlot, item.id)))
              closeSelection()
            }}
            onFocus={() => hover.showFromFocus({ type: 'item', id: item.id })}
            onMouseEnter={(event) => hover.queueFromPointer({ type: 'item', id: item.id }, event)}
            onMouseLeave={() => hover.clearIfTarget({ type: 'item', id: item.id })}
            onMouseMove={(event) => hover.updateFromPointer({ type: 'item', id: item.id }, event)}
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

interface RosterCharacter {
  key: string
  name: string
  level: number
  image?: string | null
  tags: string[]
  strength: ClientStatGrowth
  agility: ClientStatGrowth
  intelligence: ClientStatGrowth
  wisdom: ClientStatGrowth
  vitality: ClientStatGrowth
  skill?: ClientCharacterTemplate['skill']
  passive?: ClientPassiveDefinition | null
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

function orderedCharactersForSlot(position: number, slots: ClientTeamSlot[], ownedCharacters: ClientOwnedCharacter[]) {
  const available = availableCharactersForSlot(position, slots, ownedCharacters)
  const current = slots.find((slot) => slot.position === position)?.characterKey ?? null
  return [...available].sort((left, right) => {
    if (left.key === current) {
      return -1
    }
    if (right.key === current) {
      return 1
    }
    return left.name.localeCompare(right.name)
  })
}

function combinedSlotStats(character: RosterCharacter, inventory: ClientInventoryItem[], slot: ClientTeamSlot): DisplayStat[] {
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

function orderedItemsForSlot(
  inventory: ClientInventoryItem[],
  slots: ClientTeamSlot[],
  teamId: string,
  position: number,
  equipmentSlot: EquipmentSlot,
  currentItem: ClientInventoryItem | null,
) {
  const available = availableItemsForSlot(inventory, slots, teamId, position, equipmentSlot)
  const rest = available
    .filter((item) => item.id !== currentItem?.id)
    .sort((left, right) => right.itemLevel - left.itemLevel || left.itemDisplayName.localeCompare(right.itemDisplayName))
  return currentItem ? [currentItem, ...rest] : rest
}

function currentEquipmentForSlot(inventory: ClientInventoryItem[], slot: ClientTeamSlot, equipmentSlot: EquipmentSlot) {
  const itemId =
    equipmentSlot === 'WEAPON'
      ? slot.weaponItemId
      : equipmentSlot === 'ARMOR'
        ? slot.armorItemId
        : slot.accessoryItemId
  return inventory.find((item) => item.id === itemId) ?? null
}

function equipmentSlotLabel(slot: EquipmentSlot) {
  switch (slot) {
    case 'WEAPON':
      return 'Weapon'
    case 'ARMOR':
      return 'Armor'
    case 'ACCESSORY':
      return 'Accessory'
  }
}

function itemTypeAbbreviation(type: EquipmentSlot) {
  switch (type) {
    case 'WEAPON':
      return 'WP'
    case 'ARMOR':
      return 'AR'
    case 'ACCESSORY':
      return 'AC'
  }
}

function availableCharactersForSlot(position: number, slots: ClientTeamSlot[], ownedCharacters: ClientOwnedCharacter[]) {
  const assignedKeys = new Set(
    slots
      .filter((slot) => slot.position !== position)
      .map((slot) => slot.characterKey)
      .filter((key): key is string => key != null),
  )
  return ownedCharacters.filter((character) => !assignedKeys.has(character.key))
}

function setEquipmentOnSlot(slot: ClientTeamSlot, position: number, equipmentSlot: EquipmentSlot, itemId: string | null): ClientTeamSlot {
  if (slot.position !== position) {
    return slot
  }
  if (equipmentSlot === 'WEAPON') {
    return { ...slot, weaponItemId: itemId }
  }
  if (equipmentSlot === 'ARMOR') {
    return { ...slot, armorItemId: itemId }
  }
  return { ...slot, accessoryItemId: itemId }
}

function mapRosterCharacters(ownedCharacters: ClientOwnedCharacter[], templates: ClientCharacterTemplate[]): RosterCharacter[] {
  const templatesByKey = new Map(templates.map((template) => [template.key, template]))
  return ownedCharacters.map((character) => {
    const template = templatesByKey.get(character.key)
    return {
      key: character.key,
      name: character.name,
      level: character.level,
      image: template?.image,
      tags: template?.tags ?? [],
      strength: template?.strength ?? emptyGrowth(),
      agility: template?.agility ?? emptyGrowth(),
      intelligence: template?.intelligence ?? emptyGrowth(),
      wisdom: template?.wisdom ?? emptyGrowth(),
      vitality: template?.vitality ?? emptyGrowth(),
      skill: template?.skill,
      passive: template?.passive ?? null,
    }
  })
}

function growthAtLevel(growth: ClientStatGrowth, level: number) {
  return formatGrowth(numericGrowthAtLevel(growth, level))
}

function numericGrowthAtLevel(growth: ClientStatGrowth, level: number) {
  return growth.base + growth.increment * Math.max(0, level - 1)
}

function formatGrowth(value: number) {
  return Number.isInteger(value) ? value.toString() : value.toFixed(1)
}

function emptyGrowth(): ClientStatGrowth {
  return { base: 0, increment: 0 }
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

function describePassive(passive: ClientPassiveDefinition) {
  const scope = passive.leaderOnly ? 'Leader only' : 'Teamwide'
  const trigger = passive.trigger.toLowerCase().replaceAll('_', ' ')
  const condition = describeCondition(passive.condition)
  return `${scope} passive. Trigger: ${trigger}. ${condition}`
}

function describeCondition(condition: ClientCombatConditionDefinition) {
  switch (condition.type) {
    case 'ALIVE_ALLIES_WITH_TAG_AT_LEAST':
      return `Requires at least ${condition.minimumCount ?? 0} ally${condition.minimumCount === 1 ? '' : 'ies'} with ${condition.tag ?? 'a tag'}.`
    case 'ANY_ALLY_HP_BELOW_PERCENT':
    case 'SELF_HP_BELOW_PERCENT':
      return `Activates below ${condition.percent ?? 0}% HP.`
    default:
      return 'Always available.'
  }
}

function availableItemsForSlot(
  inventory: ClientInventoryItem[],
  slots: ClientTeamSlot[],
  teamId: string,
  position: number,
  equipmentSlot: EquipmentSlot,
) {
  const assignedInOtherSlots = new Set(
    slots
      .filter((slot) => slot.position !== position)
      .flatMap((slot) => [slot.weaponItemId, slot.armorItemId, slot.accessoryItemId])
      .filter((itemId): itemId is string => itemId != null),
  )
  return inventory.filter(
    (item) =>
      typeof item.itemType === 'string' &&
      item.itemType === equipmentSlot &&
      (item.equippedTeamId == null || item.equippedTeamId === teamId) &&
      !assignedInOtherSlots.has(item.id),
  )
}

function cloneSlots(slots: ClientTeamSlot[]): ClientTeamSlot[] {
  return slots.map((slot) => ({ ...slot }))
}

function slotsEqual(left: ClientTeamSlot[], right: ClientTeamSlot[]): boolean {
  if (left.length !== right.length) {
    return false
  }
  const sortedLeft = [...left].sort((a, b) => a.position - b.position)
  const sortedRight = [...right].sort((a, b) => a.position - b.position)
  return sortedLeft.every((slot, index) => {
    const other = sortedRight[index]
    return (
      slot.position === other.position &&
      slot.characterKey === other.characterKey &&
      slot.weaponItemId === other.weaponItemId &&
      slot.armorItemId === other.armorItemId &&
      slot.accessoryItemId === other.accessoryItemId
    )
  })
}

function emptySlots(): ClientTeamSlot[] {
  return [
    { position: 1, characterKey: null, weaponItemId: null, armorItemId: null, accessoryItemId: null },
    { position: 2, characterKey: null, weaponItemId: null, armorItemId: null, accessoryItemId: null },
    { position: 3, characterKey: null, weaponItemId: null, armorItemId: null, accessoryItemId: null },
  ]
}
