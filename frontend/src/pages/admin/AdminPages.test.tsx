import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { RequireAdmin } from '../../components/RequireAdmin'
import { AdminOrderPage } from './AdminOrderPage'
import { AdminProductsPage } from './AdminProductsPage'
import { Route, Routes } from 'react-router'

function respondWith(routes: Record<string, unknown>) {
  vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
    const url = String(input)
    const match = Object.keys(routes).find((path) => url.startsWith(path))
    return new Response(JSON.stringify(match ? routes[match] : {}), { status: 200 })
  })
}

function renderWithProviders(ui: React.ReactNode) {
  return render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter>{ui}</MemoryRouter>
    </QueryClientProvider>,
  )
}

afterEach(() => vi.restoreAllMocks())

describe('admin pages', () => {
  it('keeps customers out of the backoffice', async () => {
    respondWith({
      '/api/auth/session': {
        authenticated: true,
        googleEnabled: false,
        customer: { id: 1, email: 'pat@example.test', name: 'Pat', hasPassword: true, googleLinked: false, admin: false },
      },
    })

    renderWithProviders(<RequireAdmin><p>secret</p></RequireAdmin>)

    expect(await screen.findByRole('alert')).toHaveTextContent('only for administrators')
    expect(screen.queryByText('secret')).not.toBeInTheDocument()
  })

  it('lists products with their stock and status', async () => {
    respondWith({
      '/api/admin/products': [
        { id: 1, name: 'Trail Runner X', brand: 'Northpeak', category: 'Running Shoes', price: 89.9, active: true, variantCount: 5, totalStock: 31 },
        { id: 2, name: 'Old Boot', brand: 'Alpstep', category: 'Hiking Boots', price: 50, active: false, variantCount: 1, totalStock: 0 },
      ],
    })

    renderWithProviders(<AdminProductsPage />)

    expect(await screen.findByRole('link', { name: 'Trail Runner X' })).toHaveAttribute('href', '/admin/products/1')
    expect(screen.getByText('31 in 5 variants')).toBeInTheDocument()
    expect(screen.getByText('Archived')).toBeInTheDocument()
  })
  it('only offers the next step for an order', async () => {
    const order = (status: string) => ({
      order: {
        id: 7, status, placedAt: '2026-11-10T12:00:00Z', cancelledAt: null, shippedAt: null, deliveredAt: null,
        trackingNumber: status === 'SHIPPED' ? '1Z99' : null, items: [], subtotal: { amount: 10, currency: 'USD' },
        couponCode: null, discount: { amount: 0, currency: 'USD' }, shippingOption: 'STANDARD',
        shipping: { amount: 5, currency: 'USD' }, total: { amount: 15, currency: 'USD' },
        shippingAddress: { recipient: 'Pat', line1: '1 Main St', line2: null, city: 'Springfield', state: 'IL', postalCode: '62701', country: 'US' },
        cancellable: status === 'PAID',
      },
      customerId: 1, customerName: 'Pat', customerEmail: 'pat@example.test',
    })
    respondWith({ '/api/admin/orders/7': order('SHIPPED') })

    render(
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <MemoryRouter initialEntries={['/admin/orders/7']}>
          <Routes>
            <Route path="/admin/orders/:id" element={<AdminOrderPage />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    )

    expect(await screen.findByRole('button', { name: 'Mark as delivered' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Mark as shipped' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Cancel order' })).not.toBeInTheDocument()
    expect(screen.getByText('Tracking number: 1Z99')).toBeInTheDocument()
  })
})
