const SESSION_STORAGE_KEY = 'wafuriidle.session'

export interface PersistedSession {
  sessionToken: string
  sessionExpiresAt: string
  playerId: string
}

export function loadPersistedSession(): PersistedSession | null {
  const storage = browserStorage()
  if (!storage) {
    return null
  }

  const raw = storage.getItem(SESSION_STORAGE_KEY)
  if (!raw) {
    return null
  }

  try {
    const parsed = JSON.parse(raw) as Partial<PersistedSession>
    if (
      typeof parsed.sessionToken !== 'string' ||
      typeof parsed.sessionExpiresAt !== 'string' ||
      typeof parsed.playerId !== 'string'
    ) {
      storage.removeItem(SESSION_STORAGE_KEY)
      return null
    }

    if (isExpired(parsed.sessionExpiresAt)) {
      storage.removeItem(SESSION_STORAGE_KEY)
      return null
    }

    return {
      sessionToken: parsed.sessionToken,
      sessionExpiresAt: parsed.sessionExpiresAt,
      playerId: parsed.playerId,
    }
  } catch {
    storage.removeItem(SESSION_STORAGE_KEY)
    return null
  }
}

export function persistSession(session: PersistedSession | null) {
  const storage = browserStorage()
  if (!storage) {
    return
  }

  if (!session || isExpired(session.sessionExpiresAt)) {
    storage.removeItem(SESSION_STORAGE_KEY)
    return
  }

  storage.setItem(SESSION_STORAGE_KEY, JSON.stringify(session))
}

function browserStorage() {
  if (typeof window === 'undefined') {
    return null
  }

  return window.localStorage
}

function isExpired(expiresAt: string) {
  const expiresAtMillis = Date.parse(expiresAt)
  return Number.isNaN(expiresAtMillis) || expiresAtMillis <= Date.now()
}
