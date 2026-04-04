import type { HTMLAttributes, ReactNode } from 'react'

interface SurfaceCardProps extends HTMLAttributes<HTMLElement> {
  as?: 'article' | 'section' | 'div'
  children: ReactNode
}

export function SurfaceCard({ as = 'article', className = '', children, ...props }: SurfaceCardProps) {
  const Component = as
  return (
    <Component className={`workspace-card ${className}`.trim()} {...props}>
      {children}
    </Component>
  )
}
