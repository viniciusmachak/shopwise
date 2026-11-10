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

  it('ignores unknown event types instead of failing', () => {
    expect(toAssistantEvent('ui.something-new', '{}')).toBeNull()
  })

  it('ignores malformed data', () => {
    expect(toAssistantEvent('token', 'not json')).toBeNull()
  })
})
