import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { describe, expect, it } from 'vitest'
import type { OrderView } from '../api/client'
import { OrderCard } from './OrderCard'

const usd = (amount: number) => ({ amount, currency: 'USD' })

const order = (overrides: Partial<OrderView>): OrderView => ({
  id: 9, status: 'PAID', placedAt: '2026-11-21T12:00:00Z', cancelledAt: null, shippedAt: null, deliveredAt: null,
  trackingNumber: null, paymentUrl: null, items: [], subtotal: usd(10), couponCode: null, discount: usd(0),
  shippingOption: 'STANDARD', shipping: usd(5), total: usd(15),
  shippingAddress: { recipient: 'Pat', line1: '1 Main St', line2: null, city: 'Springfield', state: 'IL', postalCode: '62701', country: 'US' },
  cancellable: true,
  ...overrides,
})

describe('OrderCard', () => {
  it('offers to pay an order that waits for payment', () => {
    render(<MemoryRouter><OrderCard order={order({ status: 'PENDING_PAYMENT', paymentUrl: 'https://checkout.stripe.com/c/pay/cs_1' })} /></MemoryRouter>)

    expect(screen.getByText(/Awaiting payment/)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Pay now' })).toHaveAttribute('href', 'https://checkout.stripe.com/c/pay/cs_1')
  })

  it('has no pay link once paid', () => {
    render(<MemoryRouter><OrderCard order={order({})} /></MemoryRouter>)

    expect(screen.queryByRole('link', { name: 'Pay now' })).not.toBeInTheDocument()
  })
})
