import { useQuery } from '@tanstack/react-query'
import { useEffect, useMemo, useRef, useState, type FormEvent, type ReactNode } from 'react'
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router'
import { useCart } from '../api/cart'
import { api } from '../api/client'
import { useLogout, useSession } from '../api/session'
import { ChatPanel } from '../chat/ChatPanel'
import { ChatToggleContext } from '../chat/chatToggle'
import { t } from '../i18n'
import { CartIcon, ChatIcon, SearchIcon, UserIcon } from './icons'

export function Layout({ children }: { children: ReactNode }) {
  const navigate = useNavigate()
  const location = useLocation()
  const [params] = useSearchParams()
  const cart = useCart()
  const session = useSession()
  const logout = useLogout()
  const categories = useQuery({ queryKey: ['categories'], queryFn: api.categories })
  const searchInput = useRef<HTMLInputElement>(null)
  const [chatOpen, setChatOpen] = useState(false)
  const chat = useMemo(() => ({ open: chatOpen, setOpen: setChatOpen }), [chatOpen])
  const itemCount = cart.data?.itemCount ?? 0

  // The search box always shows the search you're looking at.
  const urlQuery = location.pathname === '/' ? (params.get('q') ?? '') : ''
  const [query, setQuery] = useState(urlQuery)
  const [shownQuery, setShownQuery] = useState(urlQuery)
  if (urlQuery !== shownQuery) {
    setShownQuery(urlQuery)
    setQuery(urlQuery)
  }

  // "/" jumps to the search box from anywhere, unless you're already typing.
  useEffect(() => {
    function onKey(event: KeyboardEvent) {
      const target = event.target
      const typing = target instanceof Element && target.closest('input, textarea, select, [contenteditable]')
      if (event.key !== '/' || typing || event.ctrlKey || event.metaKey || event.altKey) return
      event.preventDefault()
      searchInput.current?.focus()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [])

  function onSearch(event: FormEvent) {
    event.preventDefault()
    const q = query.trim()
    navigate(q ? `/?q=${encodeURIComponent(q)}` : '/')
  }

  const onShop = location.pathname === '/shop'
  const currentCategory = onShop ? params.get('category') : null

  return (
    <ChatToggleContext value={chat}>
      <div className={chatOpen ? 'app chat-open' : 'app'}>
        <a href="#content" className="skip-link">
          {t('nav.skip')}
        </a>
        <header className="header">
          <div className="header-bar">
            <Link to="/" className="logo" aria-label={t('nav.home')}>
              <span className="logo-mark" aria-hidden="true">
                S
              </span>
              {t('app.name')}
            </Link>
            <form role="search" onSubmit={onSearch} className="search">
              <input
                ref={searchInput}
                type="search"
                aria-label={t('search.placeholder')}
                aria-keyshortcuts="/"
                placeholder={t('search.placeholder')}
                value={query}
                onChange={(e) => setQuery(e.target.value)}
              />
              {!query && (
                <kbd className="kbd-hint" title={t('search.hint')}>
                  /
                </kbd>
              )}
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
                <span className="cart-label">{t('nav.cartLabel')}</span>
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
              <Link to="/shop" aria-current={onShop && !currentCategory ? 'page' : undefined}>
                {t('catalog.all')}
              </Link>
              {categories.data?.map((c) => (
                <Link
                  key={c.slug}
                  to={`/shop?category=${c.slug}`}
                  aria-current={currentCategory === c.slug ? 'page' : undefined}
                >
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
        <main className="main" id="content">
          {children}
        </main>
        <footer className="footer">
          <p>{t('footer.line')}</p>
          <p>{t('footer.help')}</p>
        </footer>
        {chatOpen && <ChatPanel />}
      </div>
    </ChatToggleContext>
  )
}
