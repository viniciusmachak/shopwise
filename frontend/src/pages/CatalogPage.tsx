import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useState, type ReactNode } from 'react'
import { Link, useSearchParams } from 'react-router'
import { api, type CategoryView } from '../api/client'
import { useSession } from '../api/session'
import { useChatToggle } from '../chat/chatToggle'
import {
  CategoryArt,
  ChevronRight,
  GridIcon,
  MicIcon,
  TagIcon,
  TruckIcon,
  BoxIcon,
  ChatIcon,
} from '../components/icons'
import { FREE_SHIPPING_FROM, ProductCard } from '../components/ProductCard'
import { formatMoney, t } from '../i18n'

const usd = (amount: number) => formatMoney({ amount, currency: 'USD' })

export function CatalogPage() {
  const [params] = useSearchParams()
  const q = params.get('q')?.trim() ?? ''
  return q ? <SearchResults query={q} /> : <Home />
}

function SearchResults({ query }: { query: string }) {
  const results = useQuery({ queryKey: ['search', query], queryFn: () => api.search(query) })
  return (
    <section className="panel">
      <h1 className="panel-title">{t('search.resultsFor', { query })}</h1>
      {results.isError && <p role="alert">{t('error.generic')}</p>}
      {results.data?.length === 0 && <p>{t('search.noResults')}</p>}
      <div className="grid">
        {results.data?.map((p) => (
          <ProductCard key={p.id} product={p} />
        ))}
      </div>
    </section>
  )
}

function Home() {
  const categories = useQuery({ queryKey: ['categories'], queryFn: api.categories })
  return (
    <>
      <Hero />
      <CategoryTiles categories={categories.data ?? []} />
      <ShortcutCards />
      {categories.data?.map((c) => (
        <CategoryShelf key={c.slug} category={c} />
      ))}
    </>
  )
}

function Hero() {
  const chat = useChatToggle()
  return (
    <section className="hero">
      <div className="hero-copy">
        <p className="hero-kicker">{t('home.kicker')}</p>
        <h1>{t('home.title')}</h1>
        <p>{t('home.subtitle')}</p>
        <div className="hero-actions">
          <button type="button" className="button button-light" onClick={() => chat.setOpen(true)}>
            <MicIcon size={18} />
            {t('home.ask')}
          </button>
          <Link to="/shop" className="button button-outline-light">
            {t('home.browse')}
          </Link>
        </div>
      </div>
      <div className="hero-art" aria-hidden="true">
        <div className="hero-bubble hero-bubble-user">{t('home.sampleQuestion')}</div>
        <div className="hero-bubble hero-bubble-bot">{t('home.sampleAnswer')}</div>
        <div className="hero-items">
          {['running-shoes', 'jackets', 'backpacks'].map((c) => (
            <span key={c} className="hero-item">
              <CategoryArt category={c} size={56} />
            </span>
          ))}
        </div>
      </div>
    </section>
  )
}

function CategoryTiles({ categories }: { categories: CategoryView[] }) {
  return (
    <nav className="category-tiles" aria-label={t('nav.categories')}>
      <Tile to="/shop" label={t('home.allProducts')} icon={<GridIcon size={30} />} />
      {categories.map((c) => (
        <Tile
          key={c.slug}
          to={`/shop?category=${c.slug}`}
          label={c.name}
          icon={<CategoryArt category={c.slug} size={42} />}
        />
      ))}
      <Tile
        to={`/shop?maxPrice=${FREE_SHIPPING_FROM}`}
        label={t('home.under', { amount: usd(FREE_SHIPPING_FROM) })}
        icon={<TagIcon size={30} />}
      />
    </nav>
  )
}

function Tile({ to, label, icon }: { to: string; label: string; icon: ReactNode }) {
  return (
    <Link to={to} className="category-tile">
      <span className="category-tile-icon">{icon}</span>
      <span>{label}</span>
    </Link>
  )
}

function ShortcutCards() {
  const chat = useChatToggle()
  const session = useSession()
  const signedIn = session.data?.authenticated
  return (
    <section className="shortcuts" aria-label={t('home.shortcuts')}>
      <Shortcut
        title={t('home.shipping.title')}
        text={t('home.shipping.text', { amount: usd(FREE_SHIPPING_FROM) })}
        icon={<TruckIcon size={34} />}
        action={<Link to="/shop">{t('home.shipping.action')}</Link>}
      />
      <Shortcut
        title={t('home.assistant.title')}
        text={t('home.assistant.text')}
        icon={<ChatIcon size={34} />}
        action={
          <button type="button" onClick={() => chat.setOpen(true)}>
            {t('home.assistant.action')}
          </button>
        }
      />
      {signedIn ? (
        <Shortcut
          title={t('home.orders.title')}
          text={t('home.orders.text')}
          icon={<BoxIcon size={34} />}
          action={<Link to="/orders">{t('home.orders.action')}</Link>}
        />
      ) : (
        <Shortcut
          title={t('home.signIn.title')}
          text={t('home.signIn.text')}
          icon={<BoxIcon size={34} />}
          action={<Link to="/sign-in">{t('nav.signIn')}</Link>}
        />
      )}
      <Shortcut
        title={t('home.under', { amount: usd(FREE_SHIPPING_FROM) })}
        text={t('home.budget.text')}
        icon={<TagIcon size={34} />}
        action={<Link to={`/shop?maxPrice=${FREE_SHIPPING_FROM}`}>{t('home.budget.action')}</Link>}
      />
    </section>
  )
}

function Shortcut({ title, text, icon, action }: { title: string; text: string; icon: ReactNode; action: ReactNode }) {
  return (
    <article className="shortcut">
      <h2>{title}</h2>
      <span className="shortcut-icon">{icon}</span>
      <p>{text}</p>
      <div className="shortcut-action">{action}</div>
    </article>
  )
}

function CategoryShelf({ category }: { category: CategoryView }) {
  const products = useQuery({
    queryKey: ['products', { category: category.slug, page: 0 }],
    queryFn: () => api.products({ category: category.slug, page: 0 }),
  })
  const items = products.data?.content ?? []
  if (items.length === 0) return null
  return (
    <section className="panel shelf">
      <div className="panel-heading">
        <h2 className="panel-title">{category.name}</h2>
        <Link to={`/shop?category=${category.slug}`} className="see-all">
          {t('home.seeAll')}
          <ChevronRight size={16} />
        </Link>
      </div>
      <div className="shelf-row">
        {items.slice(0, 6).map((p) => (
          <ProductCard key={p.id} product={p} />
        ))}
      </div>
    </section>
  )
}

/** The full product list with filters, kept in the URL so links and the back button work. */
export function ShopPage() {
  const [params, setParams] = useSearchParams()
  const category = params.get('category') ?? undefined
  const maxPrice = params.get('maxPrice') ?? ''
  const inStock = params.get('inStock') === 'true'
  const [page, setPage] = useState(0)

  const categories = useQuery({ queryKey: ['categories'], queryFn: api.categories })
  const filters = { category, maxPrice: maxPrice ? Number(maxPrice) : undefined, inStock: inStock || undefined, page }
  const products = useQuery({
    queryKey: ['products', filters],
    queryFn: () => api.products(filters),
    placeholderData: keepPreviousData,
  })

  function update(name: string, value: string | undefined) {
    const next = new URLSearchParams(params)
    if (value) next.set(name, value)
    else next.delete(name)
    setParams(next)
    setPage(0)
  }

  const current = categories.data?.find((c) => c.slug === category)

  return (
    <div className="catalog">
      <aside className="filters panel" aria-label={t('catalog.filters')}>
        <h2 className="filters-title">{t('catalog.filters')}</h2>
        <div role="group" aria-label={t('admin.category')} className="chips">
          <button type="button" aria-pressed={!category} onClick={() => update('category', undefined)}>
            {t('catalog.all')}
          </button>
          {categories.data?.map((c) => (
            <button
              key={c.slug}
              type="button"
              aria-pressed={category === c.slug}
              onClick={() => update('category', c.slug)}
            >
              {c.name}
            </button>
          ))}
        </div>
        <label className="field">
          {t('catalog.maxPrice')}
          <input
            type="number"
            min={0}
            inputMode="decimal"
            value={maxPrice}
            onChange={(e) => update('maxPrice', e.target.value || undefined)}
          />
        </label>
        <label className="check">
          <input
            type="checkbox"
            checked={inStock}
            onChange={(e) => update('inStock', e.target.checked ? 'true' : undefined)}
          />
          {t('catalog.inStock')}
        </label>
      </aside>

      <section className="panel catalog-results">
        <div className="panel-heading">
          <h1 className="panel-title">{current?.name ?? t('home.allProducts')}</h1>
          {products.data?.page && (
            <span className="muted">{t('catalog.count', { count: products.data.page.totalElements })}</span>
          )}
        </div>
        {products.isError && <p role="alert">{t('error.generic')}</p>}
        {products.data?.content?.length === 0 && <p>{t('catalog.empty')}</p>}
        <div className="grid">
          {products.data?.content?.map((p) => (
            <ProductCard key={p.id} product={p} />
          ))}
        </div>
        {products.data?.page && products.data.page.number + 1 < products.data.page.totalPages && (
          <button type="button" className="button button-soft load-more" onClick={() => setPage((p) => p + 1)}>
            {t('catalog.loadMore')}
          </button>
        )}
      </section>
    </div>
  )
}
