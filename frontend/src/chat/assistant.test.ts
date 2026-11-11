import { describe, expect, it } from 'vitest'
import { toAssistantEvent } from './assistant'

describe('toAssistantEvent', () => {
  it('maps known event types', () => {
    expect(toAssistantEvent('token', '{"text":"Hi"}')).toEqual({ type: 'token', text: 'Hi' })
    expect(toAssistantEvent('tool_status', '{"tool":"addToCart","state":"running"}')).toEqual({
      type: 'tool_status',
      tool: 'addToCart',
    })
  })

  it('maps confirmation requests and rejects unknown actions', () => {
    expect(toAssistantEvent('ui.confirm', '{"token":"t","action":"checkout","summary":{},"expiresAt":"x"}')).toMatchObject({
      type: 'ui.confirm',
      request: { token: 't', action: 'checkout' },
    })
    expect(toAssistantEvent('ui.confirm', '{"token":"t","action":"delete-everything"}')).toBeNull()
    expect(toAssistantEvent('ui.signin', '{"reason":"check out"}')).toEqual({ type: 'ui.signin' })
  })

  it('ignores unknown event types instead of failing', () => {
    expect(toAssistantEvent('ui.something-new', '{}')).toBeNull()
  })

  it('ignores malformed data', () => {
    expect(toAssistantEvent('token', 'not json')).toBeNull()
  })
})
