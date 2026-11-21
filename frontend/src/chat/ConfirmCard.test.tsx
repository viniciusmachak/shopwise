import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { CheckoutQuote, OrderView } from '../api/client'
import type { ConfirmRequest } from './assistant'
import { ConfirmCard } from './ConfirmCard'

const usd = (amount: number) => ({ amount, currency: 'USD' })

const quote: CheckoutQuote = {
  items: [{ productName: 'Trail Runner X', size: '10', quantity: 1, unitPrice: usd(89.9), lineTotal: usd(89.9) }],
  subtotal: usd(89.9),
  couponCode: null,
  discount: usd(0),
  shippingOption: 'STANDARD',
  shipping: usd(5),
  total: usd(94.9),
  shippingChoices: [],
  address: {
    id: 1, recipient: 'Pat Doe', line1: '123 Main St', line2: null, city: 'Springfield', state: 'IL',
    postalCode: '62701', country: 'US', isDefault: true,
  },
  cartHash: 'abc',
}

const order: OrderView = {
  id: 1042, status: 'PAID', placedAt: '2026-11-10T12:00:00Z', cancelledAt: null, shippedAt: null, deliveredAt: null, trackingNumber: null, paymentUrl: null,
  items: [{ productId: 1, productName: 'Trail Runner X', size: '10', color: null, quantity: 1, unitPrice: usd(89.9), lineTotal: usd(89.9) }],
  subtotal: usd(89.9), couponCode: null, discount: usd(0), shippingOption: 'STANDARD', shipping: usd(5), total: usd(94.9),
  shippingAddress: { recipient: 'Pat Doe', line1: '123 Main St', line2: null, city: 'Springfield', state: 'IL', postalCode: '62701', country: 'US' },
  cancellable: true,
}

function renderCard(request: ConfirmRequest) {
  return render(
    <QueryClientProvider client={new QueryClient()}>
      <MemoryRouter>
        <ConfirmCard request={request} />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

afterEach(() => vi.restoreAllMocks())

describe('ConfirmCard', () => {
  it('shows what will be ordered and places it only when the button is clicked', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ type: 'CHECKOUT', order }), { status: 200 }),
    )
    renderCard({ token: 'tok-1', action: 'checkout', summary: quote, expiresAt: '2026-11-10T12:10:00Z' })

    expect(screen.getByText(/Trail Runner X/)).toBeInTheDocument()
    expect(screen.getByText(/123 Main St/)).toBeInTheDocument()
    expect(fetchMock).not.toHaveBeenCalled()

    await userEvent.click(screen.getByRole('button', { name: 'Place order · $94.90' }))

    expect(fetchMock).toHaveBeenCalledWith('/api/assistant/pending-actions/tok-1/confirm', expect.objectContaining({ method: 'POST' }))
    expect(await screen.findByText('Order #1042')).toBeInTheDocument()
  })

  it('can be dismissed without placing anything', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(null, { status: 204 }))
    renderCard({ token: 'tok-2', action: 'checkout', summary: quote, expiresAt: '2026-11-10T12:10:00Z' })

    await userEvent.click(screen.getByRole('button', { name: 'Not now' }))

    expect(fetchMock).toHaveBeenCalledWith('/api/assistant/pending-actions/tok-2/cancel', expect.anything())
    expect(await screen.findByText(/nothing was changed/)).toBeInTheDocument()
  })
})
