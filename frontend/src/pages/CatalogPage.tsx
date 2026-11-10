import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { useSearchParams } from 'react-router'
import { api } from '../api/client'
import { ProductCard } from '../components/ProductCard'
import { t } from '../i18n'

export function CatalogPage() {
  const [params] = useSearchParams()
  const q = params.get('q')?.trim() ?? ''
  return q ? <SearchResults query={q} /> : <Catalog />
}

function SearchResults({ query }: { query: string }) {
  const results = useQuery({ queryKey: ['search', query], queryFn: () => api.search(query) })
  return (
    <section>
      <h1>{t('search.resultsFor', { query })}</h1>
      {results.isError && <p role="alert">{t('error.generic')}</p>}
      {results.data?.length === 0 && <p>{t('search.noResults')}</p>}
      <div className="grid">
        {results.data?.map((p) => <ProductCard key={p.id} product={p} />)}
      </div>
    </section>
  )
}

function Catalog() {
  const [category, setCategory] = useState<string | undefined>()
  const [maxPrice, setMaxPrice] = useState('')
  const [inStock, setInStock] = useState(false)
  const [page, setPage] = useState(0)

  const categories = useQuery({ queryKey: ['categories'], queryFn: api.categories })
  const filters = { category, maxPrice: maxPrice ? Number(maxPrice) : undefined, inStock: inStock || undefined, page }
  const products = useQuery({
    queryKey: ['products', filters],
    queryFn: () => api.products(filters),
    placeholderData: keepPreviousData,
  })

  const resetPage = () => setPage(0)

  return (
    <section>
      <div className="filters">
        <div role="group" aria-label="Category" className="chips">
          <button type="button" aria-pressed={!category} onClick={() => { setCategory(undefined); resetPage() }}>
            {t('catalog.all')}
          </button>
          {categories.data?.map((c) => (
            <button
              key={c.slug}
              type="button"
              aria-pressed={category === c.slug}
              onClick={() => { setCategory(c.slug); resetPage() }}
            >
              {c.name}
            </button>
          ))}
        </div>
        <label>
          {t('catalog.maxPrice')}
          <input
            type="number"
            min={0}
            inputMode="decimal"
            value={maxPrice}
            onChange={(e) => { setMaxPrice(e.target.value); resetPage() }}
          />
        </label>
        <label>
          <input type="checkbox" checked={inStock} onChange={(e) => { setInStock(e.target.checked); resetPage() }} />
          {t('catalog.inStock')}
        </label>
      </div>

      {products.isError && <p role="alert">{t('error.generic')}</p>}
      {products.data?.content?.length === 0 && <p>{t('catalog.empty')}</p>}
      <div className="grid">
        {products.data?.content?.map((p) => <ProductCard key={p.id} product={p} />)}
      </div>
      {products.data?.page && products.data.page.number + 1 < products.data.page.totalPages && (
        <button type="button" onClick={() => setPage((p) => p + 1)}>
          {t('catalog.loadMore')}
        </button>
      )}
    </section>
  )
}
