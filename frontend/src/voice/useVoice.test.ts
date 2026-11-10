import { describe, expect, it } from 'vitest'
import { isVoiceSupported, takeSentences } from './useVoice'

describe('takeSentences', () => {
  it('returns finished sentences and keeps the rest', () => {
    expect(takeSentences('Added it. Your total is $89.90! Anything')).toEqual({
      sentences: ['Added it.', 'Your total is $89.90!'],
      rest: 'Anything',
    })
  })

  it('waits for whitespace before treating the end as a sentence boundary', () => {
    expect(takeSentences('The price is 89.')).toEqual({ sentences: [], rest: 'The price is 89.' })
  })
})

describe('isVoiceSupported', () => {
  it('is false when the browser has no speech recognition', () => {
    expect(isVoiceSupported()).toBe(false)
  })
})
