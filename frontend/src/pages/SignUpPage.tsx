import { useState, type FormEvent } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { useRegister, useSession } from '../api/session'
import { t } from '../i18n'
import { safeNext } from './SignInPage'

export function SignUpPage() {
  const [params] = useSearchParams()
  const navigate = useNavigate()
  const session = useSession()
  const register = useRegister()
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const next = safeNext(params.get('next'))

  function onSubmit(event: FormEvent) {
    event.preventDefault()
    register.mutate({ email, password, name }, { onSuccess: () => navigate(next) })
  }

  return (
    <section className="auth-page">
      <h1>{t('auth.signUp')}</h1>
      <form onSubmit={onSubmit} className="form">
        <label>
          {t('auth.name')}
          <input autoComplete="name" required value={name} onChange={(e) => setName(e.target.value)} />
        </label>
        <label>
          {t('auth.email')}
          <input type="email" autoComplete="email" required value={email} onChange={(e) => setEmail(e.target.value)} />
        </label>
        <label>
          {t('auth.password')}
          <input
            type="password"
            autoComplete="new-password"
            required
            minLength={8}
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
          <span className="muted">{t('auth.passwordHint')}</span>
        </label>
        {register.isError && <p role="alert">{register.error.message}</p>}
        <button type="submit" disabled={register.isPending}>
          {t('auth.createAccount')}
        </button>
      </form>
      {session.data?.googleEnabled && (
        <p>
          <a href="/oauth2/authorization/google">{t('auth.withGoogle')}</a>
        </p>
      )}
      <p>
        {t('auth.haveAccount')} <Link to={`/sign-in?next=${encodeURIComponent(next)}`}>{t('auth.signIn')}</Link>
      </p>
    </section>
  )
}
