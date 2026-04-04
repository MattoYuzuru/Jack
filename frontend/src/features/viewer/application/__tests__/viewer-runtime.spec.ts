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
      loadNativeMetadata: async () => [{ label: 'Камера', value: 'JackCam' }],
    })

    const result = await runtime.resolve(new File(['image'], 'poster.png', { type: 'image/png' }))

    if (result.kind !== 'image') {
      throw new Error('Expected a native image preview result.')
    }

    expect(result.objectUrl).toBe('blob:preview')
    expect(result.dimensions).toEqual({ width: 1440, height: 900 })
    expect(result.format.extension).toBe('png')
    expect(result.metadata).toEqual([{ label: 'Камера', value: 'JackCam' }])
  })

  it('builds a decoded preview for heic files', async () => {
    Object.defineProperty(URL, 'createObjectURL', {
      configurable: true,
      value: vi.fn(() => 'blob:heic-preview'),
    })

    const runtime = createViewerRuntime({
      inspectNativeImage: async () => ({ width: 3024, height: 4032 }),
      decodeHeicImage: async () => ({
        bytes: new Uint8Array([1, 2, 3]),
        mimeType: 'image/jpeg',
        metadata: [{ label: 'Тип файла', value: 'HEIC' }],
        previewLabel: 'HEIC decode adapter',
      }),
    })

    const result = await runtime.resolve(
      new File(['image'], 'capture.heic', { type: 'image/heic' }),
    )

    if (result.kind !== 'image') {
      throw new Error('Expected a decoded HEIC preview result.')
    }

    expect(result.format.extension).toBe('heic')
    expect(result.previewLabel).toBe('HEIC decode adapter')
    expect(result.metadata).toEqual([{ label: 'Тип файла', value: 'HEIC' }])
  })

  it('routes raw aliases through the raw preview adapter', async () => {
    Object.defineProperty(URL, 'createObjectURL', {
      configurable: true,
      value: vi.fn(() => 'blob:raw-preview'),
    })

    const runtime = createViewerRuntime({
      inspectNativeImage: async () => ({ width: 1600, height: 1067 }),
      decodeRawImage: async () => ({
        bytes: new Uint8Array([4, 5, 6]),
        mimeType: 'image/png',
        metadata: [{ label: 'Камера', value: 'RAW Body' }],
        previewLabel: 'RAW preview extraction',
      }),
    })

    const result = await runtime.resolve(new File(['raw'], 'session.nef'))

    if (result.kind !== 'image') {
      throw new Error('Expected a RAW preview result.')
    }

    expect(result.format.extension).toBe('raw')
    expect(result.previewLabel).toBe('RAW preview extraction')
  })
})
