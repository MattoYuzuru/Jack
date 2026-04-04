import { beforeEach, describe, expect, it, vi } from 'vitest'

const { encodeRasterFrame } = vi.hoisted(() => ({
  encodeRasterFrame: vi.fn(),
}))

vi.mock('../browser-raster', () => ({
  encodeRasterFrame,
}))

import { buildSinglePagePdfFromRaster } from '../pdf-document'

describe('pdf document builder', () => {
  beforeEach(() => {
    encodeRasterFrame.mockReset()
  })

  it('wraps a rasterized jpeg payload into a single-page pdf document', async () => {
    encodeRasterFrame.mockResolvedValue(new Blob([new Uint8Array([255, 216, 255, 217])], { type: 'image/jpeg' }))

    const result = await buildSinglePagePdfFromRaster(
      {
        width: 640,
        height: 480,
        imageData: {} as ImageData,
        hasTransparency: false,
      },
      { quality: 0.9 },
    )

    const bytes = new Uint8Array(await result.arrayBuffer())
    const text = new TextDecoder().decode(bytes)

    expect(result.type).toBe('application/pdf')
    expect(new TextDecoder().decode(bytes.slice(0, 8))).toBe('%PDF-1.4')
    expect(text).toContain('/Type /Catalog')
    expect(text).toContain('/Subtype /Image')
    expect(text).toContain('/MediaBox [0 0 640 480]')
  })
})
