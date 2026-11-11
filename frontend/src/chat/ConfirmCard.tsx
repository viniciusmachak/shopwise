import { useMutation, useQueryClient } from '@tanstack/react-query'
import { cartKey } from '../api/cart'
import { api, type OrderView } from '../api/client'
import { OrderCard } from '../components/OrderCard'
import { formatMoney, t } from '../i18n'
import type { ConfirmRequest } from './assistant'

/**
 * Shows exactly what will happen and carries it out only when the customer clicks.
 * The button calls the backend directly, without going through the model.
 */
export function ConfirmCard({ request, onDone }: { request: ConfirmRequest; onDone?: (order: OrderView) => void }) {
  const client = useQueryClient()
  const confirm = useMutation({
    mutationFn: () => api.confirmAction(request.token),
    onSuccess: (result) => {
      void client.invalidateQueries({ queryKey: cartKey })
      void client.invalidateQueries({ queryKey: ['orders'] })
      onDone?.(result.order)
    },
  })
  const discard = useMutation({ mutationFn: () => api.discardAction(request.token) })

  if (confirm.data) return <OrderCard order={confirm.data.order} />
  if (discard.isSuccess) return <p className="muted">{t('confirm.dismissed')}</p>

  return (
    <div className="confirm-card" role="group" aria-label={t('confirm.label')}>
      {request.action === 'checkout' ? <CheckoutSummary request={request} /> : <CancelSummary request={request} />}
      {confirm.isError && <p role="alert">{confirm.error.message}</p>}
      <div className="actions">
        <button type="button" disabled={confirm.isPending || discard.isPending} onClick={() => confirm.mutate()}>
          {request.action === 'checkout'
            ? t('confirm.placeOrder', { total: formatMoney(request.summary.total) })
            : t('confirm.cancelOrder')}
        </button>
        <button type="button" disabled={confirm.isPending || discard.isPending} onClick={() => discard.mutate()}>
          {t('confirm.notNow')}
        </button>
      </div>
    </div>
  )
}

function CheckoutSummary({ request }: { request: Extract<ConfirmRequest, { action: 'checkout' }> }) {
  const q = request.summary
  return (
    <>
      <p>
        <strong>{t('confirm.checkoutTitle')}</strong>
      </p>
      <ul>
        {q.items.map((line, i) => (
          <li key={i}>
            {line.quantity} × {line.productName}
            {line.size ? ` (${line.size})` : ''} · {formatMoney(line.lineTotal)}
          </li>
        ))}
      </ul>
      <p>
        {t('checkout.shipping')}: {q.shipping.amount === 0 ? t('checkout.free') : formatMoney(q.shipping)}
        {q.couponCode && <> · {t('cart.discount', { code: q.couponCode })}: −{formatMoney(q.discount)}</>}
      </p>
      {q.address && (
        <p>
          {t('checkout.shipTo')}: {q.address.recipient}, {q.address.line1}, {q.address.city}
        </p>
      )}
      <p>
        {t('cart.total')}: <strong>{formatMoney(q.total)}</strong>
      </p>
    </>
  )
}

function CancelSummary({ request }: { request: Extract<ConfirmRequest, { action: 'cancel' }> }) {
  const o = request.summary
  return (
    <p>
      <strong>{t('confirm.cancelTitle', { id: o.id })}</strong>
      <br />
      {t('confirm.refund', { total: formatMoney(o.total) })}
    </p>
  )
}
