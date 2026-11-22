import { useQuery } from '@tanstack/react-query'
import { useEffect, useMemo, useRef, useState, type FormEvent, type ReactNode } from 'react'
import { Link, NavLink, useLocation, useNavigate, useSearchParams } from 'react-router'
import { useCart } from '../api/cart'
import { api, type CategoryView } from '../api/client'
import { useLogout, useSession } from '../api/session'
import { ChatPanel } from '../chat/ChatPanel'
import { ChatToggleContext } from '../chat/chatToggle'
import { formatMoney, t } from '../i18n'
import { CartIcon, ChatIcon, ChevronDown, MicIcon, SearchIcon, TruckIcon } from './icons'
import { FREE_SHIPPING_FROM } from './ProductCard'

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
  const customer = session.data?.authenticated ? session.data.customer : undefined

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

  return (
    <ChatToggleContext value={chat}>
      <div className={chatOpen ? 'app chat-open' : 'app'}>
        <a href="#content" className="skip-link">
          {t('nav.skip')}
        </a>
        <header className="header">
          <div className="header-top">
            <Link to="/" className="logo" aria-label={t('nav.home')}>
              <span className="logo-mark" aria-hidden="true">
                S
              </span>
              <span className="logo-name">{t('app.name')}</span>
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
            <button
              type="button"
              className="assistant-promo"
              onClick={() => setChatOpen((open) => !open)}
              aria-expanded={chatOpen}
            >
              {chatOpen ? <ChatIcon size={20} /> : <MicIcon size={20} />}
              <span>
                {chatOpen ? t('chat.close') : t('chat.open')}
                <small>{t('nav.assistantHint')}</small>
              </span>
            </button>
          </div>

          <div className="header-bottom">
            <p className="header-shipping">
              <TruckIcon size={20} />
              <span>
                <small>{t('nav.shippingLabel')}</small>
                {t('nav.shippingValue', { amount: formatMoney({ amount: FREE_SHIPPING_FROM, currency: 'USD' }) })}
              </span>
            </p>
            <nav className="header-links" aria-label={t('nav.store')}>
              <CategoryMenu categories={categories.data ?? []} />
              <NavLink to="/shop" end>
                {t('home.allProducts')}
              </NavLink>
              <Link to={`/shop?maxPrice=${FREE_SHIPPING_FROM}`}>
                {t('home.under', { amount: formatMoney({ amount: FREE_SHIPPING_FROM, currency: 'USD' }) })}
              </Link>
              <Link to="/shop?inStock=true">{t('nav.inStock')}</Link>
            </nav>
            <nav className="header-account" aria-label={t('nav.account')}>
              {customer ? (
                <>
                  <Link to="/account">{t('nav.greeting', { name: customer.name.split(' ')[0] || customer.name })}</Link>
                  <Link to="/orders">{t('nav.orders')}</Link>
                  {customer.admin && <Link to="/admin/products">{t('nav.admin')}</Link>}
                  <button
                    type="button"
                    className="link-button"
                    onClick={() => logout.mutate(undefined, { onSuccess: () => navigate('/') })}
                  >
                    {t('nav.signOut')}
                  </button>
                </>
              ) : (
                <>
                  <Link to="/sign-up">{t('nav.signUp')}</Link>
                  <Link to="/sign-in">{t('nav.signIn')}</Link>
                  <Link to="/orders">{t('nav.orders')}</Link>
                </>
              )}
              <Link to="/cart" className="cart-link" aria-label={t('nav.cart', { count: itemCount })}>
                <CartIcon size={24} />
                {itemCount > 0 && (
                  <span className="cart-count" aria-hidden="true">
                    {itemCount}
                  </span>
                )}
              </Link>
            </nav>
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

/** "Categories" opens a list of every category; it closes on a pick, a click outside or Escape. */
function CategoryMenu({ categories }: { categories: CategoryView[] }) {
  const [open, setOpen] = useState(false)
  const root = useRef<HTMLDivElement>(null)
  const button = useRef<HTMLButtonElement>(null)

  useEffect(() => {
    if (!open) return
    function onPointer(event: PointerEvent) {
      if (!root.current?.contains(event.target as Node)) setOpen(false)
    }
    function onKey(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        setOpen(false)
        button.current?.focus()
      }
    }
    document.addEventListener('pointerdown', onPointer)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('pointerdown', onPointer)
      document.removeEventListener('keydown', onKey)
    }
  }, [open])

  return (
    <div className="category-menu" ref={root}>
      <button
        ref={button}
        type="button"
        className="category-menu-button"
        aria-expanded={open}
        aria-controls="category-menu-list"
        onClick={() => setOpen((o) => !o)}
      >
        {t('nav.categories')}
        <ChevronDown size={16} />
      </button>
      {open && (
        <ul id="category-menu-list" className="category-menu-list">
          {categories.map((c) => (
            <li key={c.slug}>
              <Link to={`/shop?category=${c.slug}`} onClick={() => setOpen(false)}>
                {c.name}
              </Link>
            </li>
          ))}
          <li>
            <Link to="/shop" onClick={() => setOpen(false)}>
              {t('home.allProducts')}
            </Link>
          </li>
        </ul>
      )}
    </div>
  )
}
