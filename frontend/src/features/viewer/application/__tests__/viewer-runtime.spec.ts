import { afterEach, describe, expect, it, vi } from 'vitest'
import { createViewerRuntime } from '../viewer-runtime'

const originalCreateObjectUrl = URL.createObjectURL
const originalRevokeObjectUrl = URL.revokeObjectURL

afterEach(() => {
  Object.defineProperty(URL, 'createObjectURL', {
    configurable: true,
    value: originalCreateObjectUrl,
  })

  Object.defineProperty(URL, 'revokeObjectURL', {
    configurable: true,
    value: originalRevokeObjectUrl,
  })
})

describe('viewer runtime', () => {
  it('builds a native image preview for browser-supported formats', async () => {
    Object.defineProperty(URL, 'createObjectURL', {
      configurable: true,
      value: vi.fn(() => 'blob:preview'),
    })

    Object.defineProperty(URL, 'revokeObjectURL', {
      configurable: true,
      value: vi.fn(),
    })

    const runtime = createViewerRuntime({
      inspectNativeImage: async () => ({ width: 1440, height: 900 }),
    })

    const result = await runtime.resolve(new File(['image'], 'poster.png', { type: 'image/png' }))

    if (result.kind !== 'image') {
      throw new Error('Expected a native image preview result.')
    }

    expect(result.objectUrl).toBe('blob:preview')
    expect(result.dimensions).toEqual({ width: 1440, height: 900 })
    expect(result.format.extension).toBe('png')
  })

  it('returns a deferred result for pipeline-only formats', async () => {
    const runtime = createViewerRuntime()
    const result = await runtime.resolve(new File(['image'], 'capture.heic', { type: 'image/heic' }))

    if (result.kind !== 'deferred') {
      throw new Error('Expected a deferred preview result.')
    }

    expect(result.format.extension).toBe('heic')
    expect(result.nextStep).toContain('backend adapter')
  })
})
