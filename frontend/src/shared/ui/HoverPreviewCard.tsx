import type { CSSProperties, ReactNode } from 'react'

interface HoverPreviewCardProps {
  className: string
  headerClassName: string
  title: string
  badge?: ReactNode
  style: CSSProperties
  children: ReactNode
}

export function HoverPreviewCard({ className, headerClassName, title, badge, style, children }: HoverPreviewCardProps) {
  return (
    <div aria-hidden="true" className={className} style={style}>
      <div className={headerClassName}>
        <div>
          <span className="label">Preview</span>
          <h3>{title}</h3>
        </div>
        {badge ? <span className="header-chip">{badge}</span> : null}
      </div>
      {children}
    </div>
  )
}
