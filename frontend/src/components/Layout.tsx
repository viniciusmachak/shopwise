import { useQuery } from '@tanstack/react-query'
import { useMemo, useState, type FormEvent, type ReactNode } from 'react'
import { Link, useNavigate } from 'react-router'
import { useCart } from '../api/cart'
import { api } from '../api/client'
import { useLogout, useSession } from '../api/session'
import { ChatPanel } from '../chat/ChatPanel'
import { ChatToggleContext } from '../chat/chatToggle'
import { t } from '../i18n'
import { CartIcon, ChatIcon, SearchIcon, UserIcon } from './icons'

export function Layout({ children }: { children: ReactNode }) {
  const navigate = useNavigate()
  const cart = useCart()
  const session = useSession()
  const logout = useLogout()
  const categories = useQuery({ queryKey: ['categories'], queryFn: api.categories })
  const [query, setQuery] = useState('')
  const [chatOpen, setChatOpen] = useState(false)
  const chat = useMemo(() => ({ open: chatOpen, setOpen: setChatOpen }), [chatOpen])
  const itemCount = cart.data?.itemCount ?? 0

  function onSearch(event: FormEvent) {
    event.preventDefault()
    const q = query.trim()
    navigate(q ? `/?q=${encodeURIComponent(q)}` : '/')
  }

  return (
    <ChatToggleContext value={chat}>
      <div className={chatOpen ? 'app chat-open' : 'app'}>
        <header className="header">
          <div className="header-bar">
            <Link to="/" className="logo">
              <span className="logo-mark" aria-hidden="true">
                S
              </span>
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
              <button type="submit" aria-label={t('search.submit')}>
                <SearchIcon />
              </button>
            </form>
            <nav className="header-actions" aria-label={t('nav.account')}>
              {session.data?.authenticated ? (
                <>
                  <Link to="/account" className="header-link">
                    <UserIcon />
                    <span>{t('nav.account')}</span>
                  </Link>
                  <Link to="/orders" className="header-link">
                    {t('nav.orders')}
                  </Link>
                  {session.data.customer?.admin && (
                    <Link to="/admin/products" className="header-link">
                      {t('nav.admin')}
                    </Link>
                  )}
                  <button
                    type="button"
                    className="header-link"
                    onClick={() => logout.mutate(undefined, { onSuccess: () => navigate('/') })}
                  >
                    {t('nav.signOut')}
                  </button>
                </>
              ) : (
                <Link to="/sign-in" className="header-link">
                  <UserIcon />
                  <span>{t('nav.signIn')}</span>
                </Link>
              )}
              <Link to="/cart" className="header-link cart-link" aria-label={t('nav.cart', { count: itemCount })}>
                <CartIcon size={24} />
                {itemCount > 0 && (
                  <span className="cart-count" aria-hidden="true">
                    {itemCount}
                  </span>
                )}
              </Link>
            </nav>
          </div>
          <div className="header-nav">
            <nav aria-label={t('nav.categories')} className="category-nav">
              <Link to="/shop">{t('catalog.all')}</Link>
              {categories.data?.map((c) => (
                <Link key={c.slug} to={`/shop?category=${c.slug}`}>
                  {c.name}
                </Link>
              ))}
            </nav>
            <button
              type="button"
              className="assistant-toggle"
              onClick={() => setChatOpen((open) => !open)}
              aria-expanded={chatOpen}
            >
              <ChatIcon size={18} />
              {chatOpen ? t('chat.close') : t('chat.open')}
            </button>
          </div>
        </header>
        <main className="main">{children}</main>
        <footer className="footer">
          <p>{t('footer.line')}</p>
        </footer>
        {chatOpen && <ChatPanel />}
      </div>
    </ChatToggleContext>
  )
}
