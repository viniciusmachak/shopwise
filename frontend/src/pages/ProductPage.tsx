import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { useParams } from 'react-router'
import { useAddToCart } from '../api/cart'
import { api } from '../api/client'
import { CategoryArt } from '../components/icons'
import { FREE_SHIPPING_FROM, ProductCard } from '../components/ProductCard'
import { formatMoney, t } from '../i18n'

export function ProductPage() {
  const id = Number(useParams().id)
  const product = useQuery({ queryKey: ['product', id], queryFn: () => api.product(id), enabled: Number.isFinite(id) })
  const addToCart = useAddToCart()
  const recommendations = useQuery({
    queryKey: ['recommendations', id],
    queryFn: () => api.recommendations(id),
    enabled: Number.isFinite(id),
  })
  const [variantId, setVariantId] = useState<number | undefined>()

  if (product.isError) return <p role="alert">{t('product.notFound')}</p>
  if (!product.data) return null

  const p = product.data
  const variants = p.variants ?? []
  const single = variants.length === 1 ? variants[0] : undefined
  const selected = single?.id ?? variantId

  return (
    <article className="product-page">
      <div className="panel product-main">
        <div className="product-gallery">
          {p.imageUrl ? <img src={p.imageUrl} alt="" /> : <CategoryArt category={p.category} size={180} />}
        </div>
        <div className="product-info">
          <p className="muted">
            {p.brand} · {p.category}
          </p>
          <h1>{p.name}</h1>
          <p className="price">{formatMoney(p.price)}</p>
          {p.price.amount >= FREE_SHIPPING_FROM && <p className="shipping-note">{t('product.freeShipping')}</p>}
          <p>{p.description}</p>

          {!single && (
            <label>
              {t('product.size')}
              <select value={variantId ?? ''} onChange={(e) => setVariantId(Number(e.target.value) || undefined)}>
                <option value="">{t('product.chooseSize')}</option>
                {variants.map((v) => (
                  <option key={v.id} value={v.id} disabled={v.stock === 0}>
                    {v.size}
                    {v.stock === 0 ? ` (${t('product.outOfStock')})` : ''}
                  </option>
                ))}
              </select>
            </label>
          )}

          <button
            type="button"
            disabled={!selected || addToCart.isPending}
            onClick={() => selected && addToCart.mutate({ variantId: selected, quantity: 1 })}
          >
            {t('product.addToCart')}
          </button>
          <p aria-live="polite">
            {addToCart.isSuccess && t('product.added')}
            {addToCart.isError && <span role="alert">{addToCart.error.message}</span>}
          </p>
        </div>
      </div>

      {recommendations.data && recommendations.data.goesWith.length > 0 && (
        <section className="panel recommendations">
          <h2>{t('product.goesWith')}</h2>
          <div className="shelf-row">
            {recommendations.data.goesWith.map((r) => (
              <ProductCard key={r.id} product={r} />
            ))}
          </div>
        </section>
      )}
      {recommendations.data && recommendations.data.similar.length > 0 && (
        <section className="panel recommendations">
          <h2>{t('product.similar')}</h2>
          <div className="shelf-row">
            {recommendations.data.similar.map((r) => (
              <ProductCard key={r.id} product={r} />
            ))}
          </div>
        </section>
      )}
    </article>
  )
}
