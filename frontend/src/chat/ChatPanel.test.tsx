import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { describe, expect, it } from 'vitest'
import { ChatPanel } from './ChatPanel'

function renderPanel() {
  return render(
    <QueryClientProvider client={new QueryClient()}>
      <MemoryRouter>
        <ChatPanel />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('ChatPanel', () => {
  it('greets the user and hides the mic when voice is not supported', () => {
    renderPanel()

    expect(screen.getByText(/tell me what you are looking for/i)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /speak to the assistant/i })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Send' })).toBeDisabled()
  })
})
