import type { MouseEvent } from 'react'
import { CollectionTile } from '../../../shared/ui/CollectionTile'

interface InventoryTileProps {
  name: string
  type: string
  level: number
  rarity: string
  equipped: boolean
  onClick?: () => void
  onMouseEnter?: (event: MouseEvent<HTMLButtonElement>) => void
  onMouseLeave?: () => void
  onMouseMove?: (event: MouseEvent<HTMLButtonElement>) => void
  onFocus?: () => void
  onBlur?: () => void
  selected?: boolean
  compact?: boolean
}

export function InventoryTile({
  name,
  type,
  level,
  rarity,
  equipped,
  onClick,
  onMouseEnter,
  onMouseLeave,
  onMouseMove,
  onFocus,
  onBlur,
  selected = false,
  compact = false,
}: InventoryTileProps) {
  return (
    <CollectionTile
      ariaLabel={`View ${name}`}
      className={`item-tile${equipped ? ' is-equipped' : ''}`}
      compact={compact}
      name={name}
      onBlur={onBlur}
      onClick={onClick}
      onFocus={onFocus}
      onMouseEnter={onMouseEnter}
      onMouseLeave={onMouseLeave}
      onMouseMove={onMouseMove}
      portrait={
        <>
          <div aria-hidden="true" className={`item-tile__placeholder item-tile__placeholder--${type.toLowerCase()}`}>
            <span>{typeAbbreviation(type)}</span>
          </div>
          <span className="item-tile__badge">Lv {level}</span>
          <span className="item-tile__rarity">{rarity}</span>
        </>
      }
      selected={selected}
    />
  )
}

function typeAbbreviation(type: string) {
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
