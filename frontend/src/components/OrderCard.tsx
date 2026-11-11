import { Link } from 'react-router'
import type { OrderView } from '../api/client'
import { formatMoney, t } from '../i18n'
import { statusLabel } from '../pages/orderStatus'

export function OrderCard({ order }: { order: OrderView }) {
  return (
    <article className="order-card">
      <h3>
        <Link to={`/orders/${order.id}`}>{t('orders.number', { id: order.id })}</Link>
      </h3>
      <p>
        {statusLabel(order.status)} · {formatMoney(order.total)}
      </p>
      <p className="muted">
        {order.items.map((i) => `${i.quantity} × ${i.productName}${i.size ? ` (${i.size})` : ''}`).join(', ')}
      </p>
    </article>
  )
}
