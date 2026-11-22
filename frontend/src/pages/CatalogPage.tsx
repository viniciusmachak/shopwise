import { keepPreviousData, useInfiniteQuery, useQuery } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { Link, useSearchParams } from 'react-router'
import { api, type CategoryView } from '../api/client'
import { useChatToggle } from '../chat/chatToggle'
import {
  BoxIcon,
  CategoryArt,
  ChatIcon,
  ChevronRight,
  GridIcon,
  MicIcon,
  TagIcon,
  TruckIcon,
} from '../components/icons'
import { FREE_SHIPPING_FROM, ProductCard } from '../components/ProductCard'
import { Breadcrumbs, CardSkeletons, EmptyState, ErrorState } from '../components/Status'
import { formatMoney, t } from '../i18n'

const usd = (amount: number) => formatMoney({ amount, currency: 'USD' })

export function CatalogPage() {
  const [params] = useSearchParams()
  const q = params.get('q')?.trim() ?? ''
  return q ? <SearchResults query={q} /> : <Home />
}

function AskButton({ secondary = false }: { secondary?: boolean }) {
  const chat = useChatToggle()
  return (
    <button type="button" className={secondary ? 'button-secondary' : undefined} onClick={() => chat.setOpen(true)}>
      <ChatIcon size={18} />
      {t('home.ask')}
    </button>
  )
}

function SearchResults({ query }: { query: string }) {
  const results = useQuery({ queryKey: ['search', query], queryFn: () => api.search(query) })
  return (
    <section className="panel">
      <Breadcrumbs items={[{ label: t('search.resultsFor', { query }) }]} />
      <div className="panel-heading">
        <h1 className="panel-title">{t('search.resultsFor', { query })}</h1>
        {results.data && <span>{t('catalog.count', { count: results.data.length })}</span>}
      </div>
      {results.isError && <ErrorState onRetry={() => void results.refetch()} />}
      {results.data?.length === 0 && (
        <EmptyState
          title={t('search.noResults')}
          action={
            <>
              <AskButton />
              <Link to="/shop" className="button button-secondary">
                {t('home.browse')}
              </Link>
            </>
          }
        >
          {t('search.noResultsHelp')}
        </EmptyState>
      )}
      <div className="grid">
        {results.isPending && <CardSkeletons count={8} />}
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
      <Benefits />
      <CategoryTiles categories={categories.data ?? []} />
      {categories.isError && <ErrorState onRetry={() => void categories.refetch()} />}
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
      <h1>{t('home.title')}</h1>
      <p>{t('home.subtitle')}</p>
      <div className="hero-actions">
        <button type="button" className="button-light" onClick={() => chat.setOpen(true)}>
          <MicIcon size={18} />
          {t('home.ask')}
        </button>
        <Link to="/shop" className="button button-outline-light">
          {t('home.browse')}
        </Link>
      </div>
    </section>
  )
}

/** Store rules people ask about before buying, stated up front. */
function Benefits() {
  return (
    <ul className="benefits" aria-label={t('home.benefits')}>
      <li>
        <TruckIcon size={22} />
        {t('home.benefit.shipping', { amount: usd(FREE_SHIPPING_FROM) })}
      </li>
      <li>
        <ChatIcon size={22} />
        {t('home.benefit.assistant')}
      </li>
      <li>
        <BoxIcon size={22} />
        {t('home.benefit.cancel')}
      </li>
    </ul>
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

function CategoryShelf({ category }: { category: CategoryView }) {
  const products = useQuery({
    queryKey: ['products', { category: category.slug, page: 0 }],
    queryFn: () => api.products({ category: category.slug, page: 0 }),
  })
  const items = products.data?.content ?? []
  if (products.isSuccess && items.length === 0) return null
  return (
    <section className="panel shelf" aria-labelledby={`shelf-${category.slug}`}>
      <div className="panel-heading">
        <h2 className="panel-title" id={`shelf-${category.slug}`}>
          {category.name}
        </h2>
        <Link to={`/shop?category=${category.slug}`} className="see-all">
          {t('home.seeAll', { category: category.name })}
          <ChevronRight size={16} />
        </Link>
      </div>
      {products.isError && <ErrorState onRetry={() => void products.refetch()} />}
      <div className="shelf-row">
        {products.isPending && <CardSkeletons count={5} />}
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

  const categories = useQuery({ queryKey: ['categories'], queryFn: api.categories })
  const filters = { category, maxPrice: maxPrice ? Number(maxPrice) : undefined, inStock: inStock || undefined }
  // "Load more" appends the next page under the products already shown.
  const products = useInfiniteQuery({
    queryKey: ['products', 'list', filters],
    queryFn: ({ pageParam }) => api.products({ ...filters, page: pageParam }),
    initialPageParam: 0,
    getNextPageParam: (last) =>
      last.page && last.page.number + 1 < last.page.totalPages ? last.page.number + 1 : undefined,
    placeholderData: keepPreviousData,
  })
  const items = products.data?.pages.flatMap((p) => p.content ?? []) ?? []
  const total = products.data?.pages[0]?.page?.totalElements

  function update(name: string, value: string | undefined) {
    const next = new URLSearchParams(params)
    if (value) next.set(name, value)
    else next.delete(name)
    setParams(next)
  }

  function clearAll() {
    setParams(new URLSearchParams())
  }

  const current = categories.data?.find((c) => c.slug === category)
  const active = [
    current && { key: 'category', label: current.name },
    maxPrice && { key: 'maxPrice', label: t('catalog.upTo', { amount: usd(Number(maxPrice)) }) },
    inStock && { key: 'inStock', label: t('catalog.inStock') },
  ].filter((f): f is { key: string; label: string } => Boolean(f))
  const title = current?.name ?? t('home.allProducts')

  return (
    <>
      <Breadcrumbs
        items={[{ label: t('home.allProducts'), to: '/shop' }, ...(current ? [{ label: current.name }] : [])]}
      />
      <div className="catalog">
        <aside className="filters panel" aria-label={t('catalog.filters')}>
          <h2 className="filters-title">{t('catalog.filters')}</h2>
          <fieldset>
            <legend>{t('admin.category')}</legend>
            <div className="chips">
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
          </fieldset>
          <label className="field">
            {t('catalog.maxPrice')}
            <input
              type="number"
              min={0}
              step={1}
              inputMode="decimal"
              placeholder={t('catalog.maxPriceHint')}
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

        <section className="panel catalog-results" aria-busy={products.isFetching}>
          <div className="panel-heading">
            <h1 className="panel-title">{title}</h1>
            {total !== undefined && <span>{t('catalog.count', { count: total })}</span>}
          </div>
          {active.length > 0 && (
            <ul className="active-filters" aria-label={t('catalog.activeFilters')}>
              {active.map((f) => (
                <li key={f.key}>
                  <button
                    type="button"
                    className="filter-tag"
                    aria-label={t('catalog.removeFilter', { filter: f.label })}
                    onClick={() => update(f.key, undefined)}
                  >
                    {f.label} <span aria-hidden="true">×</span>
                  </button>
                </li>
              ))}
              <li>
                <button type="button" className="link-button" onClick={clearAll}>
                  {t('catalog.clearAll')}
                </button>
              </li>
            </ul>
          )}
          {products.isError && <ErrorState onRetry={() => void products.refetch()} />}
          {products.isSuccess && items.length === 0 && (
            <EmptyState
              title={t('catalog.empty')}
              action={
                <>
                  <button type="button" onClick={clearAll}>
                    {t('catalog.clearAll')}
                  </button>
                  <AskButton secondary />
                </>
              }
            >
              {t('catalog.emptyHelp')}
            </EmptyState>
          )}
          <div className="grid">
            {products.isPending && <CardSkeletons count={8} />}
            {items.map((p) => (
              <ProductCard key={p.id} product={p} />
            ))}
          </div>
          {products.hasNextPage && (
            <button
              type="button"
              className="button-secondary load-more"
              disabled={products.isFetchingNextPage}
              onClick={() => void products.fetchNextPage()}
            >
              {products.isFetchingNextPage ? t('status.loading') : t('catalog.loadMore')}
            </button>
          )}
        </section>
      </div>
    </>
  )
}
