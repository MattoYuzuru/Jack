import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createConverterCapabilityScopeFixture } from '../../../processing/application/__tests__/capability-matrix.fixtures'
import { resetProcessingCapabilityScopeCache } from '../../../processing/application/processing-client'
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

function createDecodedSource(width = 1280, height = 720, hasTransparency = false) {
  return {
    raster: createRasterFrame(width, height, hasTransparency),
    warnings: [],
  }
}

const originalFetch = globalThis.fetch

describe('converter runtime', () => {
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

  it('inspects supported files and exposes available targets', async () => {
    const runtime = createConverterRuntime()
    const prepared = await runtime.inspect(new File(['image'], 'poster.png', { type: 'image/png' }))

    expect(prepared?.source.extension).toBe('png')
    expect(prepared?.targets.map((target) => target.extension)).toEqual([
      'jpg',
      'webp',
      'avif',
      'svg',
      'ico',
      'tiff',
      'pdf',
    ])
  })

  it('routes heavy formats through their decode strategy and target encoder', async () => {
    let decoderCalls = 0
    let encoderCalls = 0

    const runtime = createConverterRuntime({
      isServerScenario: () => false,
      decodeHeicSource: async () => {
        decoderCalls += 1
        return createDecodedSource()
      },
      encodeJpeg: async () => {
        encoderCalls += 1
        return {
          blob: new Blob(['jpg'], { type: 'image/jpeg' }),
          warnings: [],
        }
      },
    })

    const prepared = await runtime.inspect(new File(['image'], 'capture.heic', { type: 'image/heic' }))

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
      decodeNativeRaster: async (_prepared: ConverterPreparedSource) => createDecodedSource(1280, 720, true),
      encodeJpeg: async () => ({
        blob: new Blob(['jpg'], { type: 'image/jpeg' }),
        warnings: [],
      }),
    })

    const prepared = await runtime.inspect(new File(['image'], 'poster.png', { type: 'image/png' }))

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
      isServerScenario: () => false,
      decodeNativeRaster: async (_prepared: ConverterPreparedSource) => createDecodedSource(1280, 720, true),
      encodePdf: async () => ({
        blob: new Blob(['pdf'], { type: 'application/pdf' }),
        warnings: ['PDF собран как single-page raster document без отдельного текстового слоя.'],
      }),
    })

    const prepared = await runtime.inspect(new File(['image'], 'sheet.png', { type: 'image/png' }))

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
      decodeNativeRaster: async (_prepared: ConverterPreparedSource) => createDecodedSource(4000, 3000, false),
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

    const prepared = await runtime.inspect(new File(['image'], 'hero.png', { type: 'image/png' }))

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
      isServerScenario: () => false,
      decodeRawSource: async (_prepared: ConverterPreparedSource) => createDecodedSource(2400, 1600, false),
      encodeTiff: async () => ({
        blob: new Blob(['tiff'], { type: 'image/tiff' }),
        previewBlob: new Blob(['png-preview'], { type: 'image/png' }),
        previewMimeType: 'image/png',
        warnings: ['TIFF собран как single-frame RGBA image.'],
      }),
    })

    const prepared = await runtime.inspect(new File(['raw'], 'capture.nef'))

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

  it('collects source warnings from illustration adapters before encode', async () => {
    const runtime = createConverterRuntime({
      isServerScenario: () => false,
      decodeIllustrationSource: async () => ({
        raster: createRasterFrame(1800, 1200, false),
        warnings: ['AI/EPS сведен через PDF-compatible render path в единый raster-слой.'],
      }),
      encodePng: async () => ({
        blob: new Blob(['png'], { type: 'image/png' }),
        warnings: [],
      }),
    })

    const prepared = await runtime.inspect(new File(['ai'], 'poster.ai'))

    if (!prepared) {
      throw new Error('Expected a prepared source for AI.')
    }

    const result = await runtime.convert({
      prepared,
      targetExtension: 'png',
    })

    expect(result.fileName).toBe('poster.png')
    expect(result.warnings).toContain('AI/EPS сведен через PDF-compatible render path в единый raster-слой.')
  })

  it('returns preview-safe blobs for avif and ico targets', async () => {
    const runtime = createConverterRuntime({
      isServerScenario: () => false,
      decodeNativeRaster: async () => createDecodedSource(1024, 768, true),
      encodeAvif: async () => ({
        blob: new Blob(['avif'], { type: 'image/avif' }),
        previewBlob: new Blob(['png-preview'], { type: 'image/png' }),
        previewMimeType: 'image/png',
        warnings: [],
      }),
      encodeIco: async () => ({
        blob: new Blob(['ico'], { type: 'image/x-icon' }),
        previewBlob: new Blob(['png-preview'], { type: 'image/png' }),
        previewMimeType: 'image/png',
        warnings: [],
      }),
    })

    const prepared = await runtime.inspect(new File(['png'], 'badge.png', { type: 'image/png' }))

    if (!prepared) {
      throw new Error('Expected a prepared source for PNG.')
    }

    const avifResult = await runtime.convert({
      prepared,
      targetExtension: 'avif',
    })
    const icoResult = await runtime.convert({
      prepared,
      targetExtension: 'ico',
    })

    expect(avifResult.blob.type).toBe('image/avif')
    expect(avifResult.previewMimeType).toBe('image/png')
    expect(icoResult.blob.type).toBe('image/x-icon')
    expect(icoResult.previewMimeType).toBe('image/png')
  })

  it('routes heavy scenarios through the backend image convert client by default', async () => {
    const runtime = createConverterRuntime({
      convertServerScenario: async ({ target, preset, onProgress }) => {
        onProgress?.('Backend IMAGE_CONVERT in progress...')

        expect(target.extension).toBe('avif')
        expect(preset.id).toBe('web-balanced')

        return {
          manifest: {
            operation: 'convert',
            sourceWidth: 3024,
            sourceHeight: 4032,
            width: 2560,
            height: 3413,
            resultMediaType: 'image/avif',
            previewMediaType: 'image/png',
            outputExtension: 'avif',
            sourceAdapterLabel: 'HEIC server rasterization',
            targetAdapterLabel: 'FFmpeg AVIF encode',
            runtimeLabel: 'HEIC server rasterization -> FFmpeg AVIF encode',
            warnings: ['Server-side resize уменьшил размерность: 3024x4032 -> 2560x3413.'],
          },
          resultBlob: new Blob(['avif'], { type: 'image/avif' }),
          previewBlob: new Blob(['png-preview'], { type: 'image/png' }),
        }
      },
    })

    const prepared = await runtime.inspect(new File(['image'], 'capture.heic', { type: 'image/heic' }))

    if (!prepared) {
      throw new Error('Expected a prepared source for HEIC.')
    }

    const progressMessages: string[] = []
    const result = await runtime.convert({
      prepared,
      targetExtension: 'avif',
      presetId: 'web-balanced',
      onProgress(message) {
        progressMessages.push(message)
      },
    })

    expect(result.fileName).toBe('capture.avif')
    expect(result.previewMimeType).toBe('image/png')
    expect(result.sourceWidth).toBe(3024)
    expect(result.width).toBe(2560)
    expect(result.warnings).toEqual([
      'Server-side resize уменьшил размерность: 3024x4032 -> 2560x3413.',
    ])
    expect(progressMessages).toContain('Backend IMAGE_CONVERT in progress...')
  })
})
