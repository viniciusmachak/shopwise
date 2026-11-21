import { Link } from 'react-router'
import { formatMoney, t } from '../i18n'
import type { Comparison } from './assistant'

function show(value: unknown): string {
  if (value === undefined || value === null) return '—'
  if (typeof value === 'boolean') return value ? t('compare.yes') : t('compare.no')
  return String(value)
}

function label(attribute: string): string {
  const words = attribute.replace(/_/g, ' ')
  return words.charAt(0).toUpperCase() + words.slice(1)
}

export function CompareCard({ comparison }: { comparison: Comparison }) {
  const { products, attributes } = comparison
  return (
    <div className="compare-card">
      <table className="admin-table">
        <caption className="visually-hidden">{t('compare.caption')}</caption>
        <thead>
          <tr>
            <th scope="col">{t('compare.product')}</th>
            {products.map((p) => (
              <th key={p.id} scope="col">
                <Link to={`/products/${p.id}`}>{p.name}</Link>
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          <tr>
            <th scope="row">{t('compare.price')}</th>
            {products.map((p) => <td key={p.id}>{formatMoney({ amount: p.price, currency: 'USD' })}</td>)}
          </tr>
          <tr>
            <th scope="row">{t('compare.brand')}</th>
            {products.map((p) => <td key={p.id}>{p.brand}</td>)}
          </tr>
          {attributes.map((a) => (
            <tr key={a}>
              <th scope="row">{label(a)}</th>
              {products.map((p) => <td key={p.id}>{show(p.attributes[a])}</td>)}
            </tr>
          ))}
          <tr>
            <th scope="row">{t('compare.sizes')}</th>
            {products.map((p) => (
              <td key={p.id}>{p.sizesInStock.length ? p.sizesInStock.join(', ') : t('product.outOfStock')}</td>
            ))}
          </tr>
        </tbody>
      </table>
    </div>
  )
}
