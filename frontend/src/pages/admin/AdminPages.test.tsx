import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { RequireAdmin } from '../../components/RequireAdmin'
import { AdminProductsPage } from './AdminProductsPage'

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
})
