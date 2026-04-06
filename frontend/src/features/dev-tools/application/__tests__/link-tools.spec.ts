import { describe, expect, it } from 'vitest'
import { analyzeLink } from '../link-tools'

describe('link tools', () => {
  it('normalizes urls and strips tracking params', () => {
    const result = analyzeLink('example.com/docs?utm_source=jack&foo=bar#hero', {
      stripTracking: true,
      removeFragment: true,
      sortParams: true,
    })

    expect(result.ok).toBe(true)
    expect(result.normalizedUrl).toBe('https://example.com/docs?utm_source=jack&foo=bar#hero')
    expect(result.cleanedUrl).toBe('https://example.com/docs?foo=bar')
    expect(result.queryEntries.find((entry) => entry.key === 'utm_source')?.status).toBe('removed')
    expect(result.warnings[0]).toContain('https://')
  })

  it('reports invalid links', () => {
    const result = analyzeLink('://broken url', {
      stripTracking: true,
      removeFragment: false,
      sortParams: false,
    })

    expect(result.ok).toBe(false)
    expect(result.error).toContain('валидный URL')
  })
})
