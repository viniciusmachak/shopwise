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
export type AdminProductRow = Schemas['AdminProductRow']
export type AdminProductView = Schemas['AdminProductView']
export type AdminVariant = Schemas['Variant']
export type ProductForm = Schemas['ProductForm']
export type VariantForm = Schemas['VariantForm']
export type AdminOrderRow = Schemas['AdminOrderRow']
export type AdminOrderView = Schemas['AdminOrderView']
export type OrderStatus = OrderView['status']
export type ImportReport = Schemas['ImportReport']
export type AssistantStatus = Schemas['AssistantStatus']
export type AssistantStats = Schemas['AssistantStats']
export type ConversationOverview = Schemas['ConversationOverview']
export type TranscriptLine = Schemas['TranscriptLine']
export type ChatMode = AssistantStatus['mode']

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
  admin: {
    products: (q: string, includeArchived: boolean) =>
      request<AdminProductRow[]>(`/api/admin/products${query({ q, includeArchived })}`),
    product: (id: number) => request<AdminProductView>(`/api/admin/products/${id}`),
    createProduct: (form: ProductForm) => post<AdminProductView>('/api/admin/products', form),
    updateProduct: (id: number, form: ProductForm) =>
      request<AdminProductView>(`/api/admin/products/${id}`, { method: 'PUT', body: JSON.stringify(form) }),
    archive: (id: number) => post<AdminProductView>(`/api/admin/products/${id}/archive`),
    restore: (id: number) => post<AdminProductView>(`/api/admin/products/${id}/restore`),
    addVariant: (id: number, variant: VariantForm, stock: number) =>
      post<AdminProductView>(`/api/admin/products/${id}/variants`, { variant, stock }),
    orders: (status: OrderStatus | '', q: string) =>
      request<AdminOrderRow[]>(`/api/admin/orders${query({ status: status || undefined, q })}`),
    order: (id: number) => request<AdminOrderView>(`/api/admin/orders/${id}`),
    shipOrder: (id: number, trackingNumber: string) =>
      post<AdminOrderView>(`/api/admin/orders/${id}/ship`, { trackingNumber: trackingNumber || null }),
    deliverOrder: (id: number) => post<AdminOrderView>(`/api/admin/orders/${id}/deliver`),
    cancelOrder: (id: number) => post<AdminOrderView>(`/api/admin/orders/${id}/cancel`),
    assistantStatus: () => request<AssistantStatus>('/api/admin/assistant/mode'),
    changeChatMode: (mode: ChatMode) =>
      request<AssistantStatus>('/api/admin/assistant/mode', { method: 'PUT', body: JSON.stringify({ mode }) }),
    assistantStats: (days: number) => request<AssistantStats>(`/api/admin/assistant/stats${query({ days })}`),
    assistantConversations: () => request<ConversationOverview[]>('/api/admin/assistant/conversations'),
    assistantTranscript: (id: string) => request<TranscriptLine[]>(`/api/admin/assistant/conversations/${id}`),
    imports: () => request<ImportReport[]>('/api/admin/imports'),
    importCatalog: async (file: File): Promise<ImportReport> => {
      const body = new FormData()
      body.append('file', file)
      // No Content-Type: the browser sets the multipart boundary.
      const response = await fetch('/api/admin/imports', {
        method: 'POST',
        credentials: 'same-origin',
        headers: { Accept: 'application/json', ...csrfHeaders('POST') },
        body,
      })
      if (!response.ok) {
        const problem = (await response.json().catch(() => ({}))) as { detail?: string }
        throw new ApiError(response.status, problem.detail ?? response.statusText)
      }
      return (await response.json()) as ImportReport
    },
    setStock: (variantId: number, quantity: number) =>
      request<AdminProductView>(`/api/admin/products/variants/${variantId}/stock`, {
        method: 'PUT',
        body: JSON.stringify({ quantity }),
      }),
  },

  confirmAction: (token: string) => post<ConfirmedAction>(`/api/assistant/pending-actions/${token}/confirm`),
  discardAction: (token: string) => post<void>(`/api/assistant/pending-actions/${token}/cancel`),
}
