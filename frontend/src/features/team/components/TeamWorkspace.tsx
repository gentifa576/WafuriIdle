import type { EquipmentSlot } from '../../../core/types/api'
import type { ClientInventoryItem, ClientOwnedCharacter, ClientTeam, ClientTeamSlot } from '../../session/model/clientModels'
import { ActionButton } from '../../../shared/ui/ActionButton'
import { Field } from '../../../shared/ui/Field'
import { SectionHeader } from '../../../shared/ui/SectionHeader'
import { SurfaceCard } from '../../../shared/ui/SurfaceCard'
import './team.css'

interface TeamWorkspaceProps {
  teams: ClientTeam[]
  selectedTeam: ClientTeam | null
  activeTeamId: string
  inventory: ClientInventoryItem[]
  ownedCharacters: ClientOwnedCharacter[]
  ownedCharacterNames: Map<string, string>
  activeTeam: ClientTeam | null
  loading: boolean
  onTeamChange: (teamId: string) => void
  onAssignCharacter: (teamId: string, position: number, characterKey: string) => void
  onEquipItem: (teamId: string, position: number, inventoryItemId: string, slot: EquipmentSlot) => void
  onUnequipItem: (teamId: string, position: number, slot: EquipmentSlot) => void
  onActivateTeam: (teamId: string) => void
}

export function TeamWorkspace({
  teams,
  selectedTeam,
  activeTeamId,
  inventory,
  ownedCharacters,
  ownedCharacterNames,
  activeTeam,
  loading,
  onTeamChange,
  onAssignCharacter,
  onEquipItem,
  onUnequipItem,
  onActivateTeam,
}: TeamWorkspaceProps) {
  const selectedSlots = selectedTeam?.slots ?? emptySlots()
  const activeSlotCount = selectedSlots.filter((slot) => slot.characterKey != null).length

  return (
    <>
      <section className="workspace-main panel">
        <section className="workspace-section">
          <SectionHeader eyebrow="Formation" title="Team Editor" aside={<span className="section-count">{activeSlotCount}/3</span>} />

          <Field label="Editing team">
            <select value={selectedTeam?.id ?? ''} onChange={(event) => onTeamChange(event.target.value)}>
              {teams.map((team, index) => (
                <option key={team.id} value={team.id}>
                  Team {index + 1}{team.id === activeTeamId ? ' · Active' : ''}
                </option>
              ))}
            </select>
          </Field>

          <div className="slot-grid team-grid">
            {selectedSlots.map((slot) => {
              const options = availableCharactersForSlot(slot.position, selectedSlots, ownedCharacters)
              const teamId = selectedTeam?.id ?? ''
              const weaponOptions = availableItemsForSlot(inventory, teamId, slot.position, 'WEAPON')
              const armorOptions = availableItemsForSlot(inventory, teamId, slot.position, 'ARMOR')
              const accessoryOptions = availableItemsForSlot(inventory, teamId, slot.position, 'ACCESSORY')
              return (
                <SurfaceCard className="slot-card" key={slot.position}>
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
                      onAssignCharacter(selectedTeam.id, slot.position, event.target.value)
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
                    onEquip={(inventoryItemId) => onEquipItem(selectedTeam!.id, slot.position, inventoryItemId, 'WEAPON')}
                    onUnequip={() => onUnequipItem(selectedTeam!.id, slot.position, 'WEAPON')}
                  />
                  <EquipmentPicker
                    label="Armor"
                    equippedItem={inventory.find((item) => item.id === slot.armorItemId) ?? null}
                    options={armorOptions}
                    disabled={loading || !selectedTeam || slot.characterKey == null}
                    onEquip={(inventoryItemId) => onEquipItem(selectedTeam!.id, slot.position, inventoryItemId, 'ARMOR')}
                    onUnequip={() => onUnequipItem(selectedTeam!.id, slot.position, 'ARMOR')}
                  />
                  <EquipmentPicker
                    label="Accessory"
                    equippedItem={inventory.find((item) => item.id === slot.accessoryItemId) ?? null}
                    options={accessoryOptions}
                    disabled={loading || !selectedTeam || slot.characterKey == null}
                    onEquip={(inventoryItemId) => onEquipItem(selectedTeam!.id, slot.position, inventoryItemId, 'ACCESSORY')}
                    onUnequip={() => onUnequipItem(selectedTeam!.id, slot.position, 'ACCESSORY')}
                  />
                </SurfaceCard>
              )
            })}
          </div>

          <div className="button-row workspace-actions">
            <ActionButton disabled={loading || !selectedTeam} onClick={() => onActivateTeam(selectedTeam!.id)} variant="secondary">
              Set As Active Team
            </ActionButton>
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
              <p>{selectedTeam?.id === activeTeam?.id ? 'You are editing the active team.' : 'You are editing a reserve team.'}</p>
            </SurfaceCard>
            <SurfaceCard>
              <span className="label">Actions</span>
              <strong>Characters and gear</strong>
              <p>A character cannot appear twice in the same team, and equipped items stay locked to their assigned slot.</p>
            </SurfaceCard>
          </div>
        </section>
      </aside>
    </>
  )
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
        {equippedItem ? <strong>{equippedItem.itemDisplayName}</strong> : <strong>Empty</strong>}
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
        <ActionButton aria-label={`Unequip ${label}`} disabled={disabled} onClick={onUnequip} slim>
          Unequip
        </ActionButton>
      ) : null}
    </div>
  )
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

function availableItemsForSlot(inventory: ClientInventoryItem[], teamId: string, position: number, equipmentSlot: EquipmentSlot) {
  return inventory.filter(
    (item) =>
      typeof item.itemType === 'string' &&
      item.itemType === equipmentSlot &&
      (item.equippedTeamId == null || (item.equippedTeamId === teamId && item.equippedPosition === position)),
  )
}

function emptySlots(): ClientTeamSlot[] {
  return [
    { position: 1, characterKey: null, weaponItemId: null, armorItemId: null, accessoryItemId: null },
    { position: 2, characterKey: null, weaponItemId: null, armorItemId: null, accessoryItemId: null },
    { position: 3, characterKey: null, weaponItemId: null, armorItemId: null, accessoryItemId: null },
  ]
}
