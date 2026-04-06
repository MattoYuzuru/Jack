import { describe, expect, it } from 'vitest'
import { analyzeTimestamp, buildBasicAuthHeader, generateUlid } from '../quick-utils'

describe('quick utils', () => {
  it('builds deterministic ulids with injected entropy', () => {
    const result = generateUlid(0, new Uint8Array(10))

    expect(result).toBe('00000000000000000000000000')
  })

  it('parses unix timestamps into utc iso strings', () => {
    const result = analyzeTimestamp('1712400000')

    expect(result.ok).toBe(true)
    expect(result.isoUtc).toBe('2024-04-06T10:40:00.000Z')
    expect(result.epochSeconds).toBe('1712400000')
  })

  it('builds basic auth headers', () => {
    const result = buildBasicAuthHeader('jack', 'secret')

    expect(result.header).toBe('Authorization: Basic amFjazpzZWNyZXQ=')
    expect(result.curlSnippet).toContain('Authorization: Basic amFjazpzZWNyZXQ=')
  })
})
