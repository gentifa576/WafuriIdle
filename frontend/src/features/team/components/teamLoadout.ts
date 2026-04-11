import type { EquipmentSlot } from '../../../core/types/api'
import type { ClientInventoryItem, ClientOwnedCharacter, ClientTeamSlot } from '../../session/model/clientModels'

export function orderedCharactersForSlot(position: number, slots: ClientTeamSlot[], ownedCharacters: ClientOwnedCharacter[]) {
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

export function orderedItemsForSlot(
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

export function currentEquipmentForSlot(inventory: ClientInventoryItem[], slot: ClientTeamSlot, equipmentSlot: EquipmentSlot) {
  const itemId =
    equipmentSlot === 'WEAPON'
      ? slot.weaponItemId
      : equipmentSlot === 'ARMOR'
        ? slot.armorItemId
        : slot.accessoryItemId
  return inventory.find((item) => item.id === itemId) ?? null
}

export function setEquipmentOnSlot(slot: ClientTeamSlot, position: number, equipmentSlot: EquipmentSlot, itemId: string | null): ClientTeamSlot {
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

export function cloneSlots(slots: ClientTeamSlot[]): ClientTeamSlot[] {
  return slots.map((slot) => ({ ...slot }))
}

export function slotsEqual(left: ClientTeamSlot[], right: ClientTeamSlot[]): boolean {
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

export function emptySlots(): ClientTeamSlot[] {
  return [
    { position: 1, characterKey: null, weaponItemId: null, armorItemId: null, accessoryItemId: null },
    { position: 2, characterKey: null, weaponItemId: null, armorItemId: null, accessoryItemId: null },
    { position: 3, characterKey: null, weaponItemId: null, armorItemId: null, accessoryItemId: null },
  ]
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
