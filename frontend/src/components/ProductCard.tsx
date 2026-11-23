import { Link } from 'react-router'
import type { ProductSummary } from '../api/client'
import { formatMoney, t } from '../i18n'
import { CategoryArt } from './icons'

/** Orders from this amount ship free (standard shipping), see ShippingRates on the backend. */
export const FREE_SHIPPING_FROM = 100

export function ProductCard({ product, compact = false }: { product: ProductSummary; compact?: boolean }) {
  return (
    <article className={compact ? 'product-card compact' : 'product-card'}>
      <Link to={`/products/${product.id}`} className="product-card-link">
        <div className="product-media">
          {product.imageUrl ? (
            <img src={product.imageUrl} alt="" loading="lazy" />
          ) : (
            <CategoryArt category={product.category} size={compact ? 56 : 80} />
          )}
          {!product.inStock && <span className="badge badge-muted">{t('product.outOfStock')}</span>}
        </div>
        <div className="product-body">
          <h3>{product.name}</h3>
          <p className="product-brand">{product.brand}</p>
          <p className="product-price">{formatMoney(product.price)}</p>
          {product.price.amount >= FREE_SHIPPING_FROM && <p className="shipping-note">{t('product.freeShipping')}</p>}
        </div>
      </Link>
    </article>
  )
}
