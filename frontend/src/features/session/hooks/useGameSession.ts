import { useEffect, useRef } from 'react'
import { currentSessionExpiresAt, currentSessionPlayerId, setSession } from '../../../core/api/httpClient'
import { getCharacterTemplates, getStarterCharacterTemplates } from '../../../core/api/playerApi'
import type { CharacterTemplate } from '../../../core/types/api'

interface UseGameSessionOptions {
  onTemplatesLoaded: (templates: CharacterTemplate[], starterTemplates: CharacterTemplate[]) => void
  onTemplatesFailed: () => void
  onRestoreStarted: (sessionExpiresAt: string | null) => void
  onRestoreFailed: () => void
  onRestoreFinished: () => void
  restorePlayerState: (playerId: string) => Promise<void>
}

export function useGameSession(options: UseGameSessionOptions) {
  const restoreAttemptedRef = useRef(false)
  const handlersRef = useRef(options)

  handlersRef.current = options

  useEffect(() => {
    void Promise.all([getCharacterTemplates(), getStarterCharacterTemplates()])
      .then(([templates, starterTemplates]) => {
        handlersRef.current.onTemplatesLoaded(templates, starterTemplates)
      })
      .catch(() => {
        handlersRef.current.onTemplatesFailed()
      })
  }, [])

  useEffect(() => {
    if (restoreAttemptedRef.current) {
      return
    }
    restoreAttemptedRef.current = true

    const playerId = currentSessionPlayerId()
    if (!playerId) {
      return
    }

    handlersRef.current.onRestoreStarted(currentSessionExpiresAt())
    void handlersRef.current.restorePlayerState(playerId)
      .catch(() => {
        setSession(null)
        handlersRef.current.onRestoreFailed()
      })
      .finally(() => {
        handlersRef.current.onRestoreFinished()
      })
  }, [])
}
