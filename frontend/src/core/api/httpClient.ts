import { env } from '../config/env'
import type { AuthResponse } from '../types/api'
import { loadPersistedSession, persistSession } from './sessionPersistence'

const persistedSession = loadPersistedSession()

let sessionToken: string | null = persistedSession?.sessionToken ?? null
let sessionExpiresAt: string | null = persistedSession?.sessionExpiresAt ?? null
let sessionPlayerId: string | null = persistedSession?.playerId ?? null

export function setSession(auth: Pick<AuthResponse, 'sessionToken' | 'sessionExpiresAt'> | null, playerId: string | null = sessionPlayerId) {
  sessionToken = auth?.sessionToken ?? null
  sessionExpiresAt = auth?.sessionExpiresAt ?? null
  sessionPlayerId = auth ? playerId : null
  syncPersistedSession()
}

export function currentSessionToken() {
  return sessionToken
}

export function currentSessionExpiresAt() {
  return sessionExpiresAt
}

export function currentSessionPlayerId() {
  return sessionPlayerId
}

export async function http<T>(path: string, init?: RequestInit): Promise<T> {
  const hasBody = init?.body != null
  const response = await fetch(`${env.apiBaseUrl}${path}`, {
    ...init,
    headers: {
      ...(hasBody ? { 'Content-Type': 'application/json' } : {}),
      ...(sessionToken ? { Authorization: `Bearer ${sessionToken}` } : {}),
      ...(init?.headers ?? {}),
    },
  })

  const refreshedToken = response.headers.get('X-Session-Token')
  if (refreshedToken) {
    setSession(
      {
        sessionToken: refreshedToken,
        sessionExpiresAt: response.headers.get('X-Session-Expires-At') ?? sessionExpiresAt ?? '',
      },
      sessionPlayerId,
    )
  }

  if (!response.ok) {
    const body = await safeJson(response)
    const text = typeof body?.message === 'string' ? body.message : await response.text()
    throw new Error(text || `HTTP ${response.status}`)
  }

  if (response.status === 204) {
    return undefined as T
  }

  const payload = (await response.json()) as T
  maybeCaptureSession(payload)
  return payload
}

async function safeJson(response: Response) {
  try {
    return (await response.clone().json()) as { message?: string } | null
  } catch {
    return null
  }
}

function maybeCaptureSession(payload: unknown) {
  if (
    payload &&
    typeof payload === 'object' &&
    'sessionToken' in payload &&
    typeof payload.sessionToken === 'string' &&
    'sessionExpiresAt' in payload &&
    typeof payload.sessionExpiresAt === 'string'
  ) {
    setSession(
      {
        sessionToken: payload.sessionToken,
        sessionExpiresAt: payload.sessionExpiresAt,
      },
      sessionPlayerId,
    )
  }
}

function syncPersistedSession() {
  if (!sessionToken || !sessionExpiresAt || !sessionPlayerId) {
    persistSession(null)
    return
  }

  persistSession({
    sessionToken,
    sessionExpiresAt,
    playerId: sessionPlayerId,
  })
}
