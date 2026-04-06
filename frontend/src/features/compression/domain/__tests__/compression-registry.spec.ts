import { afterEach, describe, expect, it, vi } from 'vitest'
import { resetProcessingCapabilityScopeCache } from '../../../processing/application/processing-client'
import { createCompressionCapabilityScopeFixture } from '../../../processing/application/__tests__/capability-matrix.fixtures'
import {
  getCompressionCapabilityMatrix,
  listCompressionTargetsForSource,
  resolveCompressionSourceFormat,
} from '../compression-registry'

const originalFetch = globalThis.fetch

describe('compression registry', () => {
  afterEach(() => {
    globalThis.fetch = originalFetch
    resetProcessingCapabilityScopeCache()
  })

  it('reads compression matrix from backend capability scope', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(
      new Response(JSON.stringify(createCompressionCapabilityScopeFixture()), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    ) as typeof fetch

    const matrix = await getCompressionCapabilityMatrix()

    expect(matrix.modes.map((mode) => mode.id)).toEqual(['maximum', 'target-size', 'custom'])
    expect(matrix.sourceFormats[0]?.extension).toBe('jpg')
  })

  it('resolves source and targets for a supported file', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(
      new Response(JSON.stringify(createCompressionCapabilityScopeFixture()), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    ) as typeof fetch

    const source = await resolveCompressionSourceFormat('poster.png', 'image/png')
    const targets = await listCompressionTargetsForSource('poster.png', 'image/png')

    expect(source?.extension).toBe('png')
    expect(targets.map((target) => target.extension)).toEqual(['jpg', 'webp'])
  })
})
