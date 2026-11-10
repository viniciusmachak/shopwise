import { describe, expect, it } from 'vitest'
import { SseParser } from './sse'

describe('SseParser', () => {
  it('parses events split across chunks', () => {
    const parser = new SseParser()

    expect(parser.push('event: token\ndata: {"text":"Hel')).toEqual([])
    expect(parser.push('lo"}\n\nevent: done\ndata: {}\n\n')).toEqual([
      { event: 'token', data: '{"text":"Hello"}' },
      { event: 'done', data: '{}' },
    ])
  })

  it('handles CRLF line endings and comments', () => {
    const parser = new SseParser()

    expect(parser.push(': keep-alive\r\nevent:ui.cart\r\ndata:{"itemCount":1}\r\n\r\n')).toEqual([
      { event: 'ui.cart', data: '{"itemCount":1}' },
    ])
  })
})
