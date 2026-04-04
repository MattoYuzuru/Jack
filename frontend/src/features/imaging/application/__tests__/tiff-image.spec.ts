import { describe, expect, it } from 'vitest'
import { buildTiffFromRaster } from '../tiff-image'

describe('tiff image builder', () => {
  it('wraps raster image data into a tiff blob', async () => {
    const raster = {
      width: 2,
      height: 1,
      imageData: {
        data: new Uint8ClampedArray([255, 0, 0, 255, 0, 255, 0, 255]),
      } as ImageData,
      hasTransparency: false,
    }

    const result = await buildTiffFromRaster(raster)
    const bytes = new Uint8Array(await result.arrayBuffer())

    expect(result.type).toBe('image/tiff')
    expect(String.fromCharCode(bytes[0] ?? 0, bytes[1] ?? 0)).toBe('MM')
    expect(bytes.length).toBeGreaterThan(32)
  })
})
