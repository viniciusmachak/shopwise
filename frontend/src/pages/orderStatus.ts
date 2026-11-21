import type { OrderView } from '../api/client'
import { t } from '../i18n'

export function statusLabel(status: OrderView['status']): string {
  switch (status) {
    case 'PAID':
      return t('orders.status.paid')
    case 'SHIPPED':
      return t('orders.status.shipped')
    case 'DELIVERED':
      return t('orders.status.delivered')
    case 'CANCELLED':
      return t('orders.status.cancelled')
    case 'PENDING_PAYMENT':
      return t('orders.status.awaitingPayment')
    default:
      return t('orders.status.processing')
  }
}

const dateFormat = new Intl.DateTimeFormat('en-US', { dateStyle: 'medium', timeStyle: 'short' })

export function formatDate(iso: string): string {
  return dateFormat.format(new Date(iso))
}
