import { describe, expect, it } from 'vitest'
import { createConverterRuntime, type ConverterPreparedSource } from '../converter-runtime'
import type { RasterImageFrame } from '../../../imaging/application/browser-raster'

function createRasterFrame(hasTransparency = false): RasterImageFrame {
  return {
    width: 1280,
    height: 720,
    imageData: {} as ImageData,
    hasTransparency,
  }
}

describe('converter runtime', () => {
  it('inspects supported files and exposes available targets', () => {
    const runtime = createConverterRuntime()
    const prepared = runtime.inspect(new File(['image'], 'poster.png', { type: 'image/png' }))

    expect(prepared?.source.extension).toBe('png')
    expect(prepared?.targets.map((target) => target.extension)).toEqual(['jpg', 'webp'])
  })

  it('routes heavy formats through their decode strategy and target encoder', async () => {
    let decoderCalls = 0
    let encoderCalls = 0

    const runtime = createConverterRuntime({
      decodeHeicSource: async () => {
        decoderCalls += 1
        return createRasterFrame()
      },
      encodeJpeg: async () => {
        encoderCalls += 1
        return new Blob(['jpg'], { type: 'image/jpeg' })
      },
    })

    const prepared = runtime.inspect(new File(['image'], 'capture.heic', { type: 'image/heic' }))

    if (!prepared) {
      throw new Error('Expected a prepared source for HEIC.')
    }

    const result = await runtime.convert({
      prepared,
      targetExtension: 'jpg',
    })

    expect(decoderCalls).toBe(1)
    expect(encoderCalls).toBe(1)
    expect(result.fileName).toBe('capture.jpg')
    expect(result.scenario.id).toBe('heic->jpg')
  })

  it('emits an alpha warning when transparent pixels go to jpeg', async () => {
    const runtime = createConverterRuntime({
      decodeNativeRaster: async (_prepared: ConverterPreparedSource) => createRasterFrame(true),
      encodeJpeg: async () => new Blob(['jpg'], { type: 'image/jpeg' }),
    })

    const prepared = runtime.inspect(new File(['image'], 'poster.png', { type: 'image/png' }))

    if (!prepared) {
      throw new Error('Expected a prepared source for PNG.')
    }

    const result = await runtime.convert({
      prepared,
      targetExtension: 'jpg',
      backgroundColor: '#ffffff',
    })

    expect(result.warnings).toEqual([
      'Прозрачные области переведены в сплошной фон перед JPEG encode.',
    ])
  })
})
