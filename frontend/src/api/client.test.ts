import { afterEach, describe, expect, it } from 'vitest'
import { csrfHeaders } from './client'

afterEach(() => {
  document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT'
})

describe('csrfHeaders', () => {
  it('echoes the XSRF cookie on state-changing requests only', () => {
    document.cookie = 'XSRF-TOKEN=abc%3D123'

    expect(csrfHeaders('POST')).toEqual({ 'X-XSRF-TOKEN': 'abc=123' })
    expect(csrfHeaders('GET')).toEqual({})
    expect(csrfHeaders(undefined)).toEqual({})
  })
})
