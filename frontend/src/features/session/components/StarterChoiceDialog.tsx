import { useEffect, useRef } from 'react'
import type { ClientCharacterTemplate } from '../model/clientModels'
import { ActionButton } from '../../../shared/ui/ActionButton'
import { SectionHeader } from '../../../shared/ui/SectionHeader'
import './session.css'

interface StarterChoiceDialogProps {
  starterTemplates: ClientCharacterTemplate[]
  selectedStarterKey: string
  loading: boolean
  error: string | null
  onSelectStarter: (key: string) => void
  onConfirmStarter: () => void
}

export function StarterChoiceDialog({
  starterTemplates,
  selectedStarterKey,
  loading,
  error,
  onSelectStarter,
  onConfirmStarter,
}: StarterChoiceDialogProps) {
  const selectedStarter = starterTemplates.find((starter) => starter.key === selectedStarterKey) ?? starterTemplates[0] ?? null
  const selectedStarterButtonRef = useRef<HTMLButtonElement | null>(null)
  const previousFocusedElementRef = useRef<HTMLElement | null>(null)

  useEffect(() => {
    previousFocusedElementRef.current = document.activeElement instanceof HTMLElement ? document.activeElement : null
    selectedStarterButtonRef.current?.focus()

    return () => {
      const previousElement = previousFocusedElementRef.current
      if (previousElement && previousElement.isConnected) {
        previousElement.focus()
        return
      }

      const fallback = document.querySelector<HTMLElement>('[data-workspace-default-focus="true"]')
      fallback?.focus()
    }
  }, [])

  return (
    <div className="starter-modal-backdrop">
      <section aria-describedby="starter-modal-description" aria-labelledby="starter-modal-title" aria-modal="true" className="starter-modal panel" role="dialog">
        <SectionHeader eyebrow="Starter Selection" title={<span id="starter-modal-title">Choose Your First Character</span>} />
        <p className="muted" id="starter-modal-description">Pick one starter to begin. This prompt will remain until your roster is no longer empty.</p>
        <div className="starter-choice-grid">
          {starterTemplates.map((starter) => (
            <ActionButton
              aria-pressed={selectedStarter?.key === starter.key}
              className={starterChoiceClass(selectedStarter?.key ?? '', starter.key)}
              key={starter.key}
              onClick={() => onSelectStarter(starter.key)}
              ref={selectedStarter?.key === starter.key ? selectedStarterButtonRef : null}
            >
              <strong>{starter.name}</strong>
              <span>{starter.key}</span>
            </ActionButton>
          ))}
        </div>
        <div className="button-row">
          <ActionButton disabled={loading || !selectedStarter} onClick={onConfirmStarter} variant="primary">
            {loading ? 'Claiming...' : 'Confirm Starter'}
          </ActionButton>
        </div>
        {error ? (
          <div aria-live="assertive" className="error-banner" role="alert">
            <span>{error}</span>
          </div>
        ) : null}
      </section>
    </div>
  )
}

function starterChoiceClass(activeKey: string, key: string) {
  return activeKey === key ? 'starter-choice is-active' : 'starter-choice'
}
