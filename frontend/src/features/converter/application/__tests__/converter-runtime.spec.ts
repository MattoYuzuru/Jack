import { describe, expect, it } from 'vitest'
import { createConverterRuntime, type ConverterPreparedSource } from '../converter-runtime'
import type { RasterImageFrame } from '../../../imaging/application/browser-raster'

function createRasterFrame(width = 1280, height = 720, hasTransparency = false): RasterImageFrame {
  return {
    width,
    height,
    imageData: {} as ImageData,
    hasTransparency,
  }
}

describe('converter runtime', () => {
  it('inspects supported files and exposes available targets', () => {
    const runtime = createConverterRuntime()
    const prepared = runtime.inspect(new File(['image'], 'poster.png', { type: 'image/png' }))

    expect(prepared?.source.extension).toBe('png')
    expect(prepared?.targets.map((target) => target.extension)).toEqual([
      'jpg',
      'tiff',
      'webp',
      'pdf',
    ])
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
        return {
          blob: new Blob(['jpg'], { type: 'image/jpeg' }),
          warnings: [],
        }
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
    expect(result.kind).toBe('image')
    expect(result.preset.id).toBe('original')
    expect(result.scenario.id).toBe('heic->jpg')
  })

  it('emits an alpha warning when transparent pixels go to jpeg', async () => {
    const runtime = createConverterRuntime({
      decodeNativeRaster: async (_prepared: ConverterPreparedSource) =>
        createRasterFrame(1280, 720, true),
      encodeJpeg: async () => ({
        blob: new Blob(['jpg'], { type: 'image/jpeg' }),
        warnings: [],
      }),
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
      'Прозрачные области переведены в сплошной фон перед JPG encode.',
    ])
  })

  it('builds document outputs through the pdf target strategy', async () => {
    const runtime = createConverterRuntime({
      decodeNativeRaster: async (_prepared: ConverterPreparedSource) =>
        createRasterFrame(1280, 720, true),
      encodePdf: async () => ({
        blob: new Blob(['pdf'], { type: 'application/pdf' }),
        warnings: ['PDF собран как single-page raster document без отдельного текстового слоя.'],
      }),
    })

    const prepared = runtime.inspect(new File(['image'], 'sheet.png', { type: 'image/png' }))

    if (!prepared) {
      throw new Error('Expected a prepared source for PNG.')
    }

    const result = await runtime.convert({
      prepared,
      targetExtension: 'pdf',
      backgroundColor: '#fffaf0',
    })

    expect(result.kind).toBe('document')
    expect(result.previewMimeType).toBe('application/pdf')
    expect(result.fileName).toBe('sheet.pdf')
    expect(result.warnings).toEqual([
      'Прозрачные области переведены в сплошной фон перед сборкой PDF.',
      'PDF собран как single-page raster document без отдельного текстового слоя.',
    ])
  })

  it('applies preset-driven resize and preset quality before encode', async () => {
    let receivedQuality: number | undefined
    let receivedPresetId = ''

    const runtime = createConverterRuntime({
      decodeNativeRaster: async (_prepared: ConverterPreparedSource) =>
        createRasterFrame(4000, 3000, false),
      resizeRaster: async (raster, preset) => {
        receivedPresetId = preset.id

        expect(raster.width).toBe(4000)
        expect(raster.height).toBe(3000)

        return {
          raster: createRasterFrame(1600, 1200, false),
          warnings: ['Preset Email Attachment уменьшил размерность: 4000x3000 -> 1600x1200.'],
        }
      },
      encodeJpeg: async (input) => {
        receivedQuality = input.quality

        return {
          blob: new Blob(['jpg'], { type: 'image/jpeg' }),
          warnings: [],
        }
      },
    })

    const prepared = runtime.inspect(new File(['image'], 'hero.png', { type: 'image/png' }))

    if (!prepared) {
      throw new Error('Expected a prepared source for PNG.')
    }

    const result = await runtime.convert({
      prepared,
      targetExtension: 'jpg',
      presetId: 'email-attachment',
    })

    expect(receivedPresetId).toBe('email-attachment')
    expect(receivedQuality).toBe(0.78)
    expect(result.preset.id).toBe('email-attachment')
    expect(result.sourceWidth).toBe(4000)
    expect(result.sourceHeight).toBe(3000)
    expect(result.width).toBe(1600)
    expect(result.height).toBe(1200)
    expect(result.warnings).toContain(
      'Preset Email Attachment уменьшил размерность: 4000x3000 -> 1600x1200.',
    )
  })

  it('returns a png preview layer for tiff targets while keeping the download blob as tiff', async () => {
    const runtime = createConverterRuntime({
      decodeRawSource: async (_prepared: ConverterPreparedSource) =>
        createRasterFrame(2400, 1600, false),
      encodeTiff: async () => ({
        blob: new Blob(['tiff'], { type: 'image/tiff' }),
        previewBlob: new Blob(['png-preview'], { type: 'image/png' }),
        previewMimeType: 'image/png',
        warnings: ['TIFF собран как single-frame RGBA image.'],
      }),
    })

    const prepared = runtime.inspect(new File(['raw'], 'capture.nef'))

    if (!prepared) {
      throw new Error('Expected a prepared source for RAW.')
    }

    const result = await runtime.convert({
      prepared,
      targetExtension: 'tiff',
    })

    expect(result.kind).toBe('image')
    expect(result.fileName).toBe('capture.tiff')
    expect(result.blob.type).toBe('image/tiff')
    expect(result.previewBlob.type).toBe('image/png')
    expect(result.previewMimeType).toBe('image/png')
  })
})
