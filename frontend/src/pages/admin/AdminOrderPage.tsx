import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useParams } from 'react-router'
import { api, type AdminOrderView } from '../../api/client'
import { AddressText } from '../../components/AddressFields'
import { AdminNav } from '../../components/AdminNav'
import { formatMoney, t } from '../../i18n'
import { formatDate, statusLabel } from '../orderStatus'

export function AdminOrderPage() {
  const id = Number(useParams().id)
  const client = useQueryClient()
  const view = useQuery({ queryKey: ['admin', 'order', id], queryFn: () => api.admin.order(id) })
  const [tracking, setTracking] = useState('')
  const [confirmCancel, setConfirmCancel] = useState(false)

  const done = (updated: AdminOrderView) => {
    client.setQueryData(['admin', 'order', id], updated)
    void client.invalidateQueries({ queryKey: ['admin', 'orders'] })
    setConfirmCancel(false)
  }
  const ship = useMutation({ mutationFn: () => api.admin.shipOrder(id, tracking.trim()), onSuccess: done })
  const deliver = useMutation({ mutationFn: () => api.admin.deliverOrder(id), onSuccess: done })
  const cancel = useMutation({ mutationFn: () => api.admin.cancelOrder(id), onSuccess: done })
  const error = ship.error ?? deliver.error ?? cancel.error

  if (view.isError) return <p role="alert">{t('orders.notFound')}</p>
  if (!view.data) return null
  const { order, customerName, customerEmail } = view.data

  return (
    <article className="admin-page">
      <AdminNav />
      <h1>{t('orders.number', { id: order.id })}</h1>
      <p>
        {statusLabel(order.status)} · {t('orders.placedOn', { date: formatDate(order.placedAt) })}
      </p>
      <p>
        {t('admin.customer')}: {customerName} · <a href={`mailto:${customerEmail}`}>{customerEmail}</a>
      </p>
      {order.trackingNumber && <p>{t('orders.tracking', { number: order.trackingNumber })}</p>}

      <ul className="cart-lines">
        {order.items.map((line, i) => (
          <li key={i}>
            <div>
              {line.quantity} × {line.productName}
              {line.size && <span className="muted"> · {t('cart.size', { size: line.size })}</span>}
            </div>
            <span>{formatMoney(line.lineTotal)}</span>
          </li>
        ))}
      </ul>
      <p>
        {t('cart.total')}: <strong>{formatMoney(order.total)}</strong>
      </p>
      <h2>{t('checkout.shipTo')}</h2>
      <AddressText address={order.shippingAddress} />

      <div className="actions">
        {order.status === 'PAID' && (
          <>
            <label>
              {t('admin.trackingNumber')}
              <input value={tracking} onChange={(e) => setTracking(e.target.value)} />
            </label>
            <button type="button" disabled={ship.isPending} onClick={() => ship.mutate()}>
              {t('admin.markShipped')}
            </button>
          </>
        )}
        {order.status === 'SHIPPED' && (
          <button type="button" disabled={deliver.isPending} onClick={() => deliver.mutate()}>
            {t('admin.markDelivered')}
          </button>
        )}
        {order.cancellable &&
          (confirmCancel ? (
            <span role="group" aria-label={t('admin.cancelQuestion')}>
              {t('admin.cancelQuestion')}{' '}
              <button type="button" disabled={cancel.isPending} onClick={() => cancel.mutate()}>
                {t('orders.cancelConfirm')}
              </button>
              <button type="button" onClick={() => setConfirmCancel(false)}>
                {t('orders.keep')}
              </button>
            </span>
          ) : (
            <button type="button" onClick={() => setConfirmCancel(true)}>
              {t('orders.cancel')}
            </button>
          ))}
      </div>
      {error && <p role="alert">{error.message}</p>}
    </article>
  )
}
