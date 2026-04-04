import { forwardRef, type ButtonHTMLAttributes, type ReactNode } from 'react'

interface ActionButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'primary' | 'secondary' | 'ghost'
  slim?: boolean
  children: ReactNode
}

export const ActionButton = forwardRef<HTMLButtonElement, ActionButtonProps>(function ActionButton(
  { variant = 'ghost', slim = false, className = '', children, type = 'button', ...props },
  ref,
) {
  const variantClass =
    variant === 'primary' ? 'primary-cta' : variant === 'secondary' ? 'secondary-button' : slim ? 'ghost-button slim' : 'ghost-button'

  return (
    <button className={`${variantClass} ${className}`.trim()} ref={ref} type={type} {...props}>
      {children}
    </button>
  )
})
