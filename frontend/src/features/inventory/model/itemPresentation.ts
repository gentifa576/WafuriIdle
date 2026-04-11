import type { EquipmentSlot } from '../../../core/types/api'

export function equipmentSlotLabel(slot: EquipmentSlot) {
  switch (slot) {
    case 'WEAPON':
      return 'Weapon'
    case 'ARMOR':
      return 'Armor'
    case 'ACCESSORY':
      return 'Accessory'
  }
}

export function itemTypeAbbreviation(type: string) {
  switch (type) {
    case 'WEAPON':
      return 'WP'
    case 'ARMOR':
      return 'AR'
    case 'ACCESSORY':
      return 'AC'
    default:
      return type.slice(0, 2).toUpperCase()
  }
}
