import { en, type MessageKey } from './en'

const messages: Record<MessageKey, string> = en

export function t(key: MessageKey, vars: Record<string, string | number> = {}): string {
  return messages[key].replace(/\{(\w+)\}/g, (match, name: string) =>
    name in vars ? String(vars[name]) : match,
  )
}

export function hasKey(key: string): key is MessageKey {
  return key in messages
}

const currency = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' })

export function formatMoney(money: { amount: number; currency: string }): string {
  if (money.currency === 'USD') return currency.format(money.amount)
  return new Intl.NumberFormat('en-US', { style: 'currency', currency: money.currency }).format(money.amount)
}
