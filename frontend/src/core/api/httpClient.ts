import { env } from '../config/env'
import type { AuthResponse } from '../types/api'

let sessionToken: string | null = null
let sessionExpiresAt: string | null = null

export function setSession(auth: Pick<AuthResponse, 'sessionToken' | 'sessionExpiresAt'> | null) {
  sessionToken = auth?.sessionToken ?? null
  sessionExpiresAt = auth?.sessionExpiresAt ?? null
}

export function currentSessionToken() {
  return sessionToken
}

export function currentSessionExpiresAt() {
  return sessionExpiresAt
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
    sessionToken = refreshedToken
    sessionExpiresAt = response.headers.get('X-Session-Expires-At')
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
    sessionToken = payload.sessionToken
    sessionExpiresAt = payload.sessionExpiresAt
  }
}
