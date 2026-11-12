import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useLocation, useParams } from 'react-router'
import { api } from '../api/client'
import { AddressText } from '../components/AddressFields'
import { formatMoney, t } from '../i18n'
import { formatDate, statusLabel } from './orderStatus'

export function OrdersPage() {
  const orders = useQuery({ queryKey: ['orders'], queryFn: api.orders })
  return (
    <section>
      <h1>{t('orders.title')}</h1>
      {orders.data?.length === 0 && <p>{t('orders.none')}</p>}
      <ul className="order-list">
        {orders.data?.map((o) => (
          <li key={o.id}>
            <Link to={`/orders/${o.id}`}>{t('orders.number', { id: o.id })}</Link> · {formatDate(o.placedAt)} ·{' '}
            {statusLabel(o.status)} · {t('orders.items', { count: o.itemCount })} · {formatMoney(o.total)}
          </li>
        ))}
      </ul>
    </section>
  )
}

export function OrderPage() {
  const id = Number(useParams().id)
  const location = useLocation()
  const client = useQueryClient()
  const order = useQuery({ queryKey: ['order', id], queryFn: () => api.order(id) })
  const [confirming, setConfirming] = useState(false)
  const cancel = useMutation({
    mutationFn: () => api.cancelOrder(id),
    onSuccess: (updated) => {
      client.setQueryData(['order', id], updated)
      void client.invalidateQueries({ queryKey: ['orders'] })
      setConfirming(false)
    },
  })

  if (order.isError) return <p role="alert">{t('orders.notFound')}</p>
  const o = order.data
  if (!o) return null
  const placed = (location.state as { placed?: boolean } | null)?.placed

  return (
    <article className="order-page">
      {placed && o.status === 'PAID' && <p role="status">{t('orders.thanks')}</p>}
      <h1>{t('orders.number', { id: o.id })}</h1>
      <p>
        {statusLabel(o.status)} · {t('orders.placedOn', { date: formatDate(o.placedAt) })}
      </p>
      {o.trackingNumber && <p>{t('orders.tracking', { number: o.trackingNumber })}</p>}
      {o.shippedAt && <p className="muted">{t('orders.shippedOn', { date: formatDate(o.shippedAt) })}</p>}
      {o.deliveredAt && <p className="muted">{t('orders.deliveredOn', { date: formatDate(o.deliveredAt) })}</p>}
      <ul className="cart-lines">
        {o.items.map((line, i) => (
          <li key={i}>
            <div>
              {line.quantity} × <Link to={`/products/${line.productId}`}>{line.productName}</Link>
              {line.size && <span className="muted"> · {t('cart.size', { size: line.size })}</span>}
            </div>
            <span>{formatMoney(line.lineTotal)}</span>
          </li>
        ))}
      </ul>
      <dl className="totals">
        <dt>{t('cart.subtotal')}</dt>
        <dd>{formatMoney(o.subtotal)}</dd>
        {o.couponCode && (
          <>
            <dt>{t('cart.discount', { code: o.couponCode })}</dt>
            <dd>−{formatMoney(o.discount)}</dd>
          </>
        )}
        <dt>{t('checkout.shipping')}</dt>
        <dd>{o.shipping.amount === 0 ? t('checkout.free') : formatMoney(o.shipping)}</dd>
        <dt>{t('cart.total')}</dt>
        <dd>
          <strong>{formatMoney(o.total)}</strong>
        </dd>
      </dl>
      <h2>{t('checkout.shipTo')}</h2>
      <AddressText address={o.shippingAddress} />

      {o.cancellable &&
        (confirming ? (
          <div role="group" aria-label={t('orders.cancelQuestion')} className="confirm-box">
            <p>{t('orders.cancelQuestion')}</p>
            <button type="button" disabled={cancel.isPending} onClick={() => cancel.mutate()}>
              {t('orders.cancelConfirm')}
            </button>
            <button type="button" onClick={() => setConfirming(false)}>
              {t('orders.keep')}
            </button>
          </div>
        ) : (
          <button type="button" onClick={() => setConfirming(true)}>
            {t('orders.cancel')}
          </button>
        ))}
      {cancel.isError && <p role="alert">{cancel.error.message}</p>}
    </article>
  )
}
