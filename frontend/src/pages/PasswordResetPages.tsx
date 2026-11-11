import { useMutation } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router'
import { api } from '../api/client'
import { t } from '../i18n'

export function ForgotPasswordPage() {
  const [email, setEmail] = useState('')
  const request = useMutation({ mutationFn: api.requestPasswordReset })

  function onSubmit(event: FormEvent) {
    event.preventDefault()
    request.mutate(email)
  }

  return (
    <section className="auth-page">
      <h1>{t('auth.resetTitle')}</h1>
      {request.isSuccess ? (
        <p role="status">{t('auth.resetSent')}</p>
      ) : (
        <form onSubmit={onSubmit} className="form">
          <label>
            {t('auth.email')}
            <input type="email" required value={email} onChange={(e) => setEmail(e.target.value)} />
          </label>
          {request.isError && <p role="alert">{request.error.message}</p>}
          <button type="submit" disabled={request.isPending}>
            {t('auth.sendResetLink')}
          </button>
        </form>
      )}
      <p>
        <Link to="/sign-in">{t('auth.backToSignIn')}</Link>
      </p>
    </section>
  )
}

export function ResetPasswordPage() {
  const [params] = useSearchParams()
  const token = params.get('token') ?? ''
  const [password, setPassword] = useState('')
  const reset = useMutation({ mutationFn: (p: string) => api.resetPassword(token, p) })

  function onSubmit(event: FormEvent) {
    event.preventDefault()
    reset.mutate(password)
  }

  return (
    <section className="auth-page">
      <h1>{t('auth.newPasswordTitle')}</h1>
      {reset.isSuccess ? (
        <p role="status">
          {t('auth.passwordChanged')} <Link to="/sign-in">{t('auth.signIn')}</Link>
        </p>
      ) : (
        <form onSubmit={onSubmit} className="form">
          <label>
            {t('auth.newPassword')}
            <input
              type="password"
              autoComplete="new-password"
              required
              minLength={8}
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
          </label>
          {reset.isError && <p role="alert">{reset.error.message}</p>}
          <button type="submit" disabled={reset.isPending || !token}>
            {t('auth.savePassword')}
          </button>
        </form>
      )}
    </section>
  )
}
