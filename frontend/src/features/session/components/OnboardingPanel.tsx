import { FeedbackState } from '../../workspace/components/FeedbackState'
import { ActionButton } from '../../../shared/ui/ActionButton'
import { Field } from '../../../shared/ui/Field'
import { SectionHeader } from '../../../shared/ui/SectionHeader'

import './session.css'

interface OnboardingPanelProps {
  authMode: 'guest' | 'signup' | 'login'
  playerName: string
  authEmail: string
  authPassword: string
  guestNamePlaceholder: string
  loading: boolean
  error: string | null
  onAuthModeChange: (mode: 'guest' | 'signup' | 'login') => void
  onPlayerNameChange: (value: string) => void
  onAuthEmailChange: (value: string) => void
  onAuthPasswordChange: (value: string) => void
  onCreateGuest: () => void
  onRandomizeGuestName: () => void
  onSignUp: () => void
  onLogin: () => void
  onClearError: () => void
}

export function OnboardingPanel({
  authMode,
  playerName,
  authEmail,
  authPassword,
  guestNamePlaceholder,
  loading,
  error,
  onAuthModeChange,
  onPlayerNameChange,
  onAuthEmailChange,
  onAuthPasswordChange,
  onCreateGuest,
  onRandomizeGuestName,
  onSignUp,
  onLogin,
  onClearError,
}: OnboardingPanelProps) {
  return (
    <main className="hud-shell onboarding-shell compact-auth-shell">
      <section className="onboarding-hero">
        <p className="eyebrow">WafuriIdle</p>
        <h1>Battle, drift offline, return richer.</h1>
        <p className="hero-copy">
          This client is now aligned with the real backend contract. Create a guest adventurer, open the live socket,
          and let the server own the fight.
        </p>
      </section>

      <section className="auth-panel panel">
        <SectionHeader eyebrow="Access" title="Enter the game" aside={<div className="header-chip">Server-authoritative</div>} />

        <div className="auth-mode-row">
          <ActionButton aria-pressed={authMode === 'guest'} className={authModeClass(authMode, 'guest')} onClick={() => onAuthModeChange('guest')}>
            Guest
          </ActionButton>
          <ActionButton aria-pressed={authMode === 'signup'} className={authModeClass(authMode, 'signup')} onClick={() => onAuthModeChange('signup')}>
            Sign Up
          </ActionButton>
          <ActionButton aria-pressed={authMode === 'login'} className={authModeClass(authMode, 'login')} onClick={() => onAuthModeChange('login')}>
            Log In
          </ActionButton>
        </div>

        <Field label={authMode === 'login' ? 'Name or email' : 'Player name'}>
          <input
            autoComplete={authMode === 'login' ? 'username' : 'nickname'}
            placeholder={guestNamePlaceholder}
            value={playerName}
            onChange={(event) => onPlayerNameChange(event.target.value)}
          />
        </Field>

        {authMode === 'signup' ? (
          <Field label="Email">
            <input autoComplete="email" placeholder="you@example.com" value={authEmail} onChange={(event) => onAuthEmailChange(event.target.value)} />
          </Field>
        ) : null}

        {authMode !== 'guest' ? (
          <Field label="Password">
            <input
              autoComplete={authMode === 'signup' ? 'new-password' : 'current-password'}
              type="password"
              value={authPassword}
              onChange={(event) => onAuthPasswordChange(event.target.value)}
            />
          </Field>
        ) : null}

        {authMode === 'guest' ? (
          <div className="button-row">
            <ActionButton disabled={loading} onClick={onCreateGuest} variant="primary">
              {loading ? 'Entering...' : 'Create Guest'}
            </ActionButton>
            <ActionButton disabled={loading} onClick={onRandomizeGuestName}>
              Randomize
            </ActionButton>
          </div>
        ) : null}

        {authMode === 'signup' ? (
          <ActionButton
            disabled={loading || playerName.trim().length === 0 || authPassword.trim().length === 0}
            onClick={onSignUp}
            variant="primary"
          >
            {loading ? 'Creating account...' : 'Sign Up'}
          </ActionButton>
        ) : null}

        {authMode === 'login' ? (
          <ActionButton
            disabled={loading || playerName.trim().length === 0 || authPassword.trim().length === 0}
            onClick={onLogin}
            variant="primary"
          >
            {loading ? 'Signing in...' : 'Log In'}
          </ActionButton>
        ) : null}

        {error ? (
          <div aria-live="assertive" role="alert">
            <FeedbackState
              title="Sign-in problem"
              detail={error}
              tone="danger"
              actions={
                <ActionButton onClick={onClearError} slim>
                  Clear
                </ActionButton>
              }
            />
          </div>
        ) : null}

        <div className="onboarding-footnote">
          <span>Combat start is now routed through WebSocket ownership.</span>
          <span>Offline progression and zone notifications surface here once connected.</span>
        </div>
      </section>
    </main>
  )
}

function authModeClass(activeMode: 'guest' | 'signup' | 'login', item: 'guest' | 'signup' | 'login') {
  return activeMode === item ? 'auth-mode-button is-active' : 'auth-mode-button'
}
