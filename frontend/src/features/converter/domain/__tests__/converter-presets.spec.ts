import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createConverterCapabilityScopeFixture } from '../../../processing/application/__tests__/capability-matrix.fixtures'
import { resetProcessingCapabilityScopeCache } from '../../../processing/application/processing-client'
import { listConverterPresets, resolveConverterPreset } from '../converter-presets'

const originalFetch = globalThis.fetch

describe('converter presets', () => {
  beforeEach(() => {
    resetProcessingCapabilityScopeCache()
    globalThis.fetch = vi.fn().mockResolvedValue(
      new Response(JSON.stringify(createConverterCapabilityScopeFixture()), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    ) as typeof fetch
  })

  afterEach(() => {
    resetProcessingCapabilityScopeCache()
    globalThis.fetch = originalFetch
  })

  it('exposes the stable preset catalogue in the intended order', async () => {
    expect((await listConverterPresets()).map((preset) => preset.id)).toEqual([
      'original',
      'web-balanced',
      'email-attachment',
      'thumbnail',
    ])
  })

  it('falls back to original when preset is unknown', async () => {
    expect((await resolveConverterPreset('missing')).id).toBe('original')
    expect((await resolveConverterPreset('web-balanced')).id).toBe('web-balanced')
  })
})
