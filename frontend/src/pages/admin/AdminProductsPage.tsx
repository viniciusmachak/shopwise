import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router'
import { api } from '../../api/client'
import { formatMoney, t } from '../../i18n'

export function AdminProductsPage() {
  const [q, setQ] = useState('')
  const [includeArchived, setIncludeArchived] = useState(true)
  const products = useQuery({
    queryKey: ['admin', 'products', q, includeArchived],
    queryFn: () => api.admin.products(q, includeArchived),
    placeholderData: keepPreviousData,
  })

  return (
    <section className="admin-page">
      <h1>{t('admin.products')}</h1>
      <div className="filters">
        <label>
          {t('admin.searchProducts')}
          <input type="search" value={q} onChange={(e) => setQ(e.target.value)} />
        </label>
        <label>
          <input type="checkbox" checked={includeArchived} onChange={(e) => setIncludeArchived(e.target.checked)} />
          {t('admin.showArchived')}
        </label>
        <Link to="/admin/products/new">{t('admin.newProduct')}</Link>
      </div>
      <table className="admin-table">
        <thead>
          <tr>
            <th>{t('admin.name')}</th>
            <th>{t('admin.brand')}</th>
            <th>{t('admin.category')}</th>
            <th>{t('admin.price')}</th>
            <th>{t('admin.stock')}</th>
            <th>{t('admin.status')}</th>
          </tr>
        </thead>
        <tbody>
          {products.data?.map((p) => (
            <tr key={p.id}>
              <td>
                <Link to={`/admin/products/${p.id}`}>{p.name}</Link>
              </td>
              <td>{p.brand}</td>
              <td>{p.category}</td>
              <td>{formatMoney({ amount: p.price, currency: 'USD' })}</td>
              <td>{t('admin.stockSummary', { total: p.totalStock, variants: p.variantCount })}</td>
              <td>{p.active ? t('admin.active') : t('admin.archived')}</td>
            </tr>
          ))}
        </tbody>
      </table>
      {products.data?.length === 0 && <p>{t('admin.noProducts')}</p>}
    </section>
  )
}
