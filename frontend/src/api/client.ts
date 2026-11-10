import type { components } from './schema'

export type ProductSummary = components['schemas']['ProductSummary']
export type ProductDetail = components['schemas']['ProductDetail']
export type CategoryView = components['schemas']['CategoryView']
export type CartView = components['schemas']['CartView']
export type CartLine = components['schemas']['Line']
export type Money = components['schemas']['Money']
export type ProductPage = components['schemas']['PagedModelProductSummary']

export class ApiError extends Error {
  readonly status: number

  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, {
    credentials: 'same-origin',
    ...init,
    headers: { Accept: 'application/json', 'Content-Type': 'application/json', ...init?.headers },
  })
  if (!response.ok) {
    let message = response.statusText
    try {
      const problem = (await response.json()) as { detail?: string }
      if (problem.detail) message = problem.detail
    } catch {
      // not a problem-detail body
    }
    throw new ApiError(response.status, message)
  }
  return (await response.json()) as T
}

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
  addToCart: (variantId: number, quantity: number) =>
    request<CartView>('/api/cart/items', { method: 'POST', body: JSON.stringify({ variantId, quantity }) }),
  updateCartItem: (itemId: number, quantity: number) =>
    request<CartView>(`/api/cart/items/${itemId}`, { method: 'PATCH', body: JSON.stringify({ quantity }) }),
  applyCoupon: (code: string) =>
    request<CartView>('/api/cart/coupon', { method: 'POST', body: JSON.stringify({ code }) }),
  removeCoupon: () => request<CartView>('/api/cart/coupon', { method: 'DELETE' }),
}
