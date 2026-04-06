import type { MouseEvent, ReactNode } from 'react'

interface CharacterRosterTileProps {
  name: string
  image?: string | null
  badge?: ReactNode
  onClick?: () => void
  onMouseEnter?: (event: MouseEvent<HTMLButtonElement>) => void
  onMouseLeave?: () => void
  onMouseMove?: (event: MouseEvent<HTMLButtonElement>) => void
  onFocus?: () => void
  onBlur?: () => void
  selected?: boolean
  compact?: boolean
}

export function CharacterRosterTile({
  name,
  image,
  badge,
  onClick,
  onMouseEnter,
  onMouseLeave,
  onMouseMove,
  onFocus,
  onBlur,
  selected = false,
  compact = false,
}: CharacterRosterTileProps) {
  return (
    <button
      aria-label={`View ${name}`}
      className={`character-tile${selected ? ' is-selected' : ''}${compact ? ' character-tile--compact' : ''}`}
      onBlur={onBlur}
      onClick={onClick}
      onFocus={onFocus}
      onMouseEnter={onMouseEnter}
      onMouseLeave={onMouseLeave}
      onMouseMove={onMouseMove}
      type="button"
    >
      <div className="character-tile__portrait-frame">
        {image ? (
          <img alt="" className="character-tile__portrait" loading="lazy" src={image} />
        ) : (
          <div aria-hidden="true" className="character-tile__fallback">
            {initials(name)}
          </div>
        )}
        {badge ? <span className="character-tile__badge">{badge}</span> : null}
      </div>
      <div className="character-tile__copy">
        <strong>{name}</strong>
      </div>
    </button>
  )
}

function initials(name: string) {
  return name
    .split(/\s+/)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase() ?? '')
    .join('')
}
