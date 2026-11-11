import type { components } from './schema'

type Schemas = components['schemas']

export type ProductSummary = Schemas['ProductSummary']
export type ProductDetail = Schemas['ProductDetail']
export type CategoryView = Schemas['CategoryView']
export type CartView = Schemas['CartView']
export type CartLine = Schemas['CartLine']
export type Money = Schemas['Money']
export type ProductPage = Schemas['PagedModelProductSummary']
export type SessionView = Schemas['SessionView']
export type MeView = Schemas['MeView']
export type AddressView = Schemas['AddressView']
export type AddressForm = Schemas['AddressForm']
export type CheckoutQuote = Schemas['CheckoutQuote']
export type ShippingOption = CheckoutQuote['shippingOption']
export type OrderView = Schemas['OrderView']
export type OrderSummary = Schemas['OrderSummary']
export type ConversationSummary = Schemas['ConversationSummary']
export type HistoryMessage = Schemas['HistoryMessage']
export type ConfirmedAction = Schemas['ConfirmedAction']

export class ApiError extends Error {
  readonly status: number
  readonly reason?: string

  constructor(status: number, message: string, reason?: string) {
    super(message)
    this.status = status
    this.reason = reason
  }
}

/** Spring Security's SPA CSRF protection: echo the XSRF-TOKEN cookie in a header. */
export function csrfHeaders(method: string | undefined): Record<string, string> {
  if (!method || ['GET', 'HEAD', 'OPTIONS'].includes(method.toUpperCase())) return {}
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/)
  return match?.[1] ? { 'X-XSRF-TOKEN': decodeURIComponent(match[1]) } : {}
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, {
    credentials: 'same-origin',
    ...init,
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
      ...csrfHeaders(init?.method),
      ...init?.headers,
    },
  })
  if (!response.ok) {
    let message = response.statusText
    let reason: string | undefined
    try {
      const problem = (await response.json()) as { detail?: string; reason?: string }
      if (problem.detail) message = problem.detail
      reason = problem.reason
    } catch {
      // not a problem-detail body
    }
    throw new ApiError(response.status, message, reason)
  }
  if (response.status === 204 || response.status === 202) return undefined as T
  return (await response.json()) as T
}

const post = <T>(path: string, body?: unknown) =>
  request<T>(path, { method: 'POST', body: body === undefined ? undefined : JSON.stringify(body) })

function query(params: Record<string, string | number | boolean | undefined>): string {
  const search = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== '') search.set(key, String(value))
  }
  const text = search.toString()
  return text ? `?${text}` : ''
}

export interface ProductFilters {
  category?: string
  maxPrice?: number
  inStock?: boolean
  page?: number
}

export const api = {
  categories: () => request<CategoryView[]>('/api/categories'),
  products: (filters: ProductFilters) => request<ProductPage>(`/api/products${query({ ...filters })}`),
  product: (id: number) => request<ProductDetail>(`/api/products/${id}`),
  search: (q: string) => request<ProductSummary[]>(`/api/search${query({ q })}`),

  cart: () => request<CartView>('/api/cart'),
  addToCart: (variantId: number, quantity: number) => post<CartView>('/api/cart/items', { variantId, quantity }),
  updateCartItem: (itemId: number, quantity: number) =>
    request<CartView>(`/api/cart/items/${itemId}`, { method: 'PATCH', body: JSON.stringify({ quantity }) }),
  applyCoupon: (code: string) => post<CartView>('/api/cart/coupon', { code }),
  removeCoupon: () => request<CartView>('/api/cart/coupon', { method: 'DELETE' }),

  session: () => request<SessionView>('/api/auth/session'),
  register: (email: string, password: string, name: string) =>
    post<MeView>('/api/auth/register', { email, password, name }),
  login: (email: string, password: string) => post<MeView>('/api/auth/login', { email, password }),
  logout: () => post<void>('/api/auth/logout'),
  requestPasswordReset: (email: string) => post<void>('/api/auth/password-reset', { email }),
  resetPassword: (token: string, password: string) =>
    post<void>('/api/auth/password-reset/confirm', { token, password }),

  updateProfile: (name: string) =>
    request<MeView>('/api/me', { method: 'PATCH', body: JSON.stringify({ name }) }),
  addresses: () => request<AddressView[]>('/api/me/addresses'),
  addAddress: (form: AddressForm) => post<AddressView>('/api/me/addresses', form),
  updateAddress: (id: number, form: AddressForm) =>
    request<AddressView>(`/api/me/addresses/${id}`, { method: 'PUT', body: JSON.stringify(form) }),
  deleteAddress: (id: number) => request<void>(`/api/me/addresses/${id}`, { method: 'DELETE' }),
  makeDefaultAddress: (id: number) => post<AddressView>(`/api/me/addresses/${id}/default`),

  quote: (addressId?: number, shipping?: ShippingOption) => post<CheckoutQuote>('/api/checkout/quote', { addressId, shipping }),
  placeOrder: (addressId: number, shipping: ShippingOption, cartHash: string, idempotencyKey: string) =>
    post<OrderView>('/api/checkout', { addressId, shipping, cartHash, idempotencyKey }),
  orders: () => request<OrderSummary[]>('/api/orders'),
  order: (id: number) => request<OrderView>(`/api/orders/${id}`),
  cancelOrder: (id: number) => post<OrderView>(`/api/orders/${id}/cancel`),

  conversations: () => request<ConversationSummary[]>('/api/assistant/conversations'),
  conversation: (id: string) => request<HistoryMessage[]>(`/api/assistant/conversations/${id}`),
  confirmAction: (token: string) => post<ConfirmedAction>(`/api/assistant/pending-actions/${token}/confirm`),
  discardAction: (token: string) => post<void>(`/api/assistant/pending-actions/${token}/cancel`),
}
