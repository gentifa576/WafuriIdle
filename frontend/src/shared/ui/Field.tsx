import type { ReactNode } from 'react'

interface FieldProps {
  label: ReactNode
  children: ReactNode
  className?: string
}

export function Field({ label, children, className = '' }: FieldProps) {
  return (
    <label className={`field ${className}`.trim()}>
      <span>{label}</span>
      {children}
    </label>
  )
}
