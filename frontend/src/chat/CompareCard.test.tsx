import { render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { describe, expect, it } from 'vitest'
import { toAssistantEvent } from './assistant'
import { CompareCard } from './CompareCard'

const comparison = {
  products: [
    { id: 1, name: 'Trail Runner X', brand: 'Northpeak', category: 'Running Shoes', price: 89.9,
      attributes: { waterproof: false, drop_mm: 6 }, sizesInStock: ['9', '11'] },
    { id: 3, name: 'Stormguard GTX Runner', brand: 'Northpeak', category: 'Running Shoes', price: 139,
      attributes: { waterproof: true }, sizesInStock: [] },
  ],
  attributes: ['drop_mm', 'waterproof'],
}

describe('CompareCard', () => {
  it('lines products up by attribute', () => {
    render(<MemoryRouter><CompareCard comparison={comparison} /></MemoryRouter>)

    const waterproof = screen.getByRole('row', { name: /Waterproof/ })
    expect(within(waterproof).getAllByRole('cell').map((c) => c.textContent)).toEqual(['No', 'Yes'])
    const drop = screen.getByRole('row', { name: /Drop mm/ })
    expect(within(drop).getAllByRole('cell').map((c) => c.textContent)).toEqual(['6', '—'])
    expect(screen.getByRole('row', { name: /Sizes in stock/ })).toHaveTextContent('Sold out')
  })

  it('ignores malformed comparison events', () => {
    expect(toAssistantEvent('ui.compare', '{"products": "nope"}')).toBeNull()
    expect(toAssistantEvent('ui.compare', JSON.stringify(comparison))).toMatchObject({ type: 'ui.compare' })
  })
})
