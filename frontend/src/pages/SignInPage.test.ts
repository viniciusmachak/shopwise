import { describe, expect, it } from 'vitest'
import { safeNext } from './SignInPage'

describe('safeNext', () => {
  it('only allows paths on this site', () => {
    expect(safeNext('/checkout')).toBe('/checkout')
    expect(safeNext('//evil.example')).toBe('/')
    expect(safeNext('https://evil.example')).toBe('/')
    expect(safeNext(null)).toBe('/')
  })
})
