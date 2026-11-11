import { useState, type FormEvent, type ReactNode } from 'react'
import { Link, useNavigate } from 'react-router'
import { useCart } from '../api/cart'
import { useLogout, useSession } from '../api/session'
import { ChatPanel } from '../chat/ChatPanel'
import { t } from '../i18n'

export function Layout({ children }: { children: ReactNode }) {
  const navigate = useNavigate()
  const cart = useCart()
  const session = useSession()
  const logout = useLogout()
  const [query, setQuery] = useState('')
  const [chatOpen, setChatOpen] = useState(false)

  function onSearch(event: FormEvent) {
    event.preventDefault()
    const q = query.trim()
    navigate(q ? `/?q=${encodeURIComponent(q)}` : '/')
  }

  return (
    <div className={chatOpen ? 'app chat-open' : 'app'}>
      <header className="header">
        <Link to="/" className="logo">
          {t('app.name')}
        </Link>
        <form role="search" onSubmit={onSearch} className="search">
          <input
            type="search"
            aria-label={t('search.placeholder')}
            placeholder={t('search.placeholder')}
            value={query}
            onChange={(e) => setQuery(e.target.value)}
          />
          <button type="submit">{t('search.submit')}</button>
        </form>
        <Link to="/cart">{t('nav.cart', { count: cart.data?.itemCount ?? 0 })}</Link>
        {session.data?.authenticated ? (
          <>
            <Link to="/orders">{t('nav.orders')}</Link>
            <Link to="/account">{t('nav.account')}</Link>
            <button type="button" onClick={() => logout.mutate(undefined, { onSuccess: () => navigate('/') })}>
              {t('nav.signOut')}
            </button>
          </>
        ) : (
          <Link to="/sign-in">{t('nav.signIn')}</Link>
        )}
        <button type="button" onClick={() => setChatOpen((open) => !open)} aria-expanded={chatOpen}>
          {chatOpen ? t('chat.close') : t('chat.open')}
        </button>
      </header>
      <main className="main">{children}</main>
      {chatOpen && <ChatPanel />}
    </div>
  )
}
