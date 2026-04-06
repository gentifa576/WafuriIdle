import type { MouseEvent, ReactNode } from 'react'
import '../styles/collectionTile.css'

interface CollectionTileProps {
  ariaLabel: string
  name: string
  className?: string
  selected?: boolean
  compact?: boolean
  onClick?: () => void
  onMouseEnter?: (event: MouseEvent<HTMLButtonElement>) => void
  onMouseLeave?: () => void
  onMouseMove?: (event: MouseEvent<HTMLButtonElement>) => void
  onFocus?: () => void
  onBlur?: () => void
  portrait: ReactNode
  copyAside?: ReactNode
}

export function CollectionTile({
  ariaLabel,
  name,
  className = '',
  selected = false,
  compact = false,
  onClick,
  onMouseEnter,
  onMouseLeave,
  onMouseMove,
  onFocus,
  onBlur,
  portrait,
  copyAside,
}: CollectionTileProps) {
  return (
    <button
      aria-label={ariaLabel}
      className={`collection-tile${selected ? ' is-selected' : ''}${compact ? ' collection-tile--compact' : ''} ${className}`.trim()}
      onBlur={onBlur}
      onClick={onClick}
      onFocus={onFocus}
      onMouseEnter={onMouseEnter}
      onMouseLeave={onMouseLeave}
      onMouseMove={onMouseMove}
      type="button"
    >
      <div className="collection-tile__portrait-frame">{portrait}</div>
      <div className="collection-tile__copy">
        <strong>{name}</strong>
        {copyAside}
      </div>
    </button>
  )
}
