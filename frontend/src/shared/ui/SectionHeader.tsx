import type { ReactNode } from 'react'

interface SectionHeaderProps {
  eyebrow?: ReactNode
  title: ReactNode
  aside?: ReactNode
}

export function SectionHeader({ eyebrow, title, aside }: SectionHeaderProps) {
  return (
    <div className="section-heading">
      <div>
        {eyebrow ? <p className="eyebrow">{eyebrow}</p> : null}
        <h2>{title}</h2>
      </div>
      {aside}
    </div>
  )
}
