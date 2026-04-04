import type { ReactNode } from 'react'
import './workspace.css'

interface FeedbackStateProps {
  title: string
  detail: string
  tone?: 'neutral' | 'warning' | 'danger' | 'success'
  actions?: ReactNode
}

export function FeedbackState({ title, detail, tone = 'neutral', actions }: FeedbackStateProps) {
  return (
    <section className={`feedback-state tone-${tone}`}>
      <div className="feedback-copy">
        <strong>{title}</strong>
        <p>{detail}</p>
      </div>
      {actions ? <div className="feedback-actions">{actions}</div> : null}
    </section>
  )
}
