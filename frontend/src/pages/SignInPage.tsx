import { useState, type FormEvent } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { useLogin, useSession } from '../api/session'
import { t } from '../i18n'

export function safeNext(next: string | null): string {
  return next && next.startsWith('/') && !next.startsWith('//') ? next : '/'
}

export function SignInPage() {
  const [params] = useSearchParams()
  const navigate = useNavigate()
  const session = useSession()
  const login = useLogin()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const next = safeNext(params.get('next'))

  function onSubmit(event: FormEvent) {
    event.preventDefault()
    login.mutate({ email, password }, { onSuccess: () => navigate(next) })
  }

  return (
    <section className="auth-page">
      <h1>{t('auth.signIn')}</h1>
      {params.get('error') === 'google' && <p role="alert">{t('auth.googleFailed')}</p>}
      <form onSubmit={onSubmit} className="form">
        <label>
          {t('auth.email')}
          <input type="email" autoComplete="email" required value={email} onChange={(e) => setEmail(e.target.value)} />
        </label>
        <label>
          {t('auth.password')}
          <input
            type="password"
            autoComplete="current-password"
            required
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
        </label>
        {login.isError && <p role="alert">{login.error.message}</p>}
        <button type="submit" disabled={login.isPending}>
          {t('auth.signIn')}
        </button>
      </form>
      {session.data?.googleEnabled && (
        <p>
          <a href="/oauth2/authorization/google">{t('auth.withGoogle')}</a>
        </p>
      )}
      <p>
        <Link to="/forgot-password">{t('auth.forgot')}</Link>
      </p>
      <p>
        {t('auth.noAccount')} <Link to={`/sign-up?next=${encodeURIComponent(next)}`}>{t('auth.signUp')}</Link>
      </p>
    </section>
  )
}
