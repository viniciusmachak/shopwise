import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { RequireAdmin } from '../../components/RequireAdmin'
import userEvent from '@testing-library/user-event'
import { AdminAssistantPage } from './AdminAssistantPage'
import { ImportResult } from './AdminImportPage'
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
        trackingNumber: status === 'SHIPPED' ? '1Z99' : null, paymentUrl: null, items: [], subtotal: { amount: 10, currency: 'USD' },
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
  it('shows import results with skipped lines', () => {
    render(
      <ImportResult
        report={{
          id: 1, fileName: 'c.csv', status: 'COMPLETED', startedAt: '2026-11-11T12:00:00Z', read: 6, imported: 3,
          skipped: 3, errors: [{ line: 5, message: 'Missing price.' }],
        }}
      />,
    )

    expect(screen.getByRole('status')).toHaveTextContent('6 rows read, 3 imported, 3 skipped.')
    expect(screen.getByText(/Line 5:/)).toHaveTextContent('Missing price.')
  })
  it('switches the chat mode from the dashboard', async () => {
    const status = (mode: string) => ({
      mode, configuredMode: 'LOCAL', changedAt: null, localModel: 'qwen2.5:7b', fallbackToLocal: true,
      cloudProviders: [{ name: 'gemini', model: 'gemini-2.5-flash', configured: true }],
    })
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
      const url = String(input)
      if (url.startsWith('/api/admin/assistant/mode')) {
        return new Response(JSON.stringify(status(init?.method === 'PUT' ? 'CLOUD' : 'LOCAL')), { status: 200 })
      }
      if (url.startsWith('/api/admin/assistant/stats')) {
        return new Response(JSON.stringify({ days: 7, conversations: 4, userMessages: 9, checkoutsPrepared: 2,
          ordersPlaced: 1, conversionRate: 0.25, tools: [], usage: [] }), { status: 200 })
      }
      return new Response('[]', { status: 200 })
    })

    renderWithProviders(<AdminAssistantPage />)

    expect(await screen.findByText('25%')).toBeInTheDocument()
    await userEvent.click(await screen.findByRole('radio', { name: 'Cloud (free-tier APIs)' }))

    expect(fetchMock).toHaveBeenCalledWith('/api/admin/assistant/mode', expect.objectContaining({ method: 'PUT', body: '{"mode":"CLOUD"}' }))
    expect(await screen.findByRole('radio', { name: 'Cloud (free-tier APIs)' })).toBeChecked()
  })
})
