import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router'
import { api, type OrderStatus } from '../../api/client'
import { AdminNav } from '../../components/AdminNav'
import { formatMoney, t } from '../../i18n'
import { formatDate, statusLabel } from '../orderStatus'

const STATUSES: OrderStatus[] = ['PAID', 'SHIPPED', 'DELIVERED', 'CANCELLED']

export function AdminOrdersPage() {
  const [status, setStatus] = useState<OrderStatus | ''>('PAID')
  const [q, setQ] = useState('')
  const orders = useQuery({
    queryKey: ['admin', 'orders', status, q],
    queryFn: () => api.admin.orders(status, q),
    placeholderData: keepPreviousData,
  })

  return (
    <section className="admin-page">
      <AdminNav />
      <h1>{t('admin.orders')}</h1>
      <div className="filters">
        <label>
          {t('admin.status')}
          <select value={status} onChange={(e) => setStatus(e.target.value as OrderStatus | '')}>
            <option value="">{t('admin.allStatuses')}</option>
            {STATUSES.map((s) => (
              <option key={s} value={s}>
                {statusLabel(s)}
              </option>
            ))}
          </select>
        </label>
        <label>
          {t('admin.searchOrders')}
          <input type="search" value={q} onChange={(e) => setQ(e.target.value)} />
        </label>
      </div>
      <table className="admin-table">
        <thead>
          <tr>
            <th>{t('admin.order')}</th>
            <th>{t('admin.placed')}</th>
            <th>{t('admin.customer')}</th>
            <th>{t('admin.items')}</th>
            <th>{t('cart.total')}</th>
            <th>{t('admin.status')}</th>
          </tr>
        </thead>
        <tbody>
          {orders.data?.map((o) => (
            <tr key={o.id}>
              <td>
                <Link to={`/admin/orders/${o.id}`}>#{o.id}</Link>
              </td>
              <td>{formatDate(o.placedAt)}</td>
              <td>
                {o.customerName} · {o.customerEmail}
              </td>
              <td>{o.itemCount}</td>
              <td>{formatMoney(o.total)}</td>
              <td>{statusLabel(o.status)}</td>
            </tr>
          ))}
        </tbody>
      </table>
      {orders.data?.length === 0 && <p>{t('admin.noOrders')}</p>}
    </section>
  )
}
