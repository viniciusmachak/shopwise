import { Link } from 'react-router'
import type { ProductSummary } from '../api/client'
import { formatMoney, t } from '../i18n'

export function ProductCard({ product, compact = false }: { product: ProductSummary; compact?: boolean }) {
  return (
    <article className={compact ? 'product-card compact' : 'product-card'}>
      <Link to={`/products/${product.id}`}>
        <h3>{product.name}</h3>
      </Link>
      <p className="muted">
        {product.brand} · {product.category}
      </p>
      <p>
        <strong>{formatMoney(product.price)}</strong>
        {!product.inStock && <span className="badge">{t('product.outOfStock')}</span>}
      </p>
    </article>
  )
}
