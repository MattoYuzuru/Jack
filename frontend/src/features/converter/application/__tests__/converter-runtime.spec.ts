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
      isServerScenario: () => false,
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
      isServerScenario: () => false,
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

  it('routes png to jpg through backend by default after converter route flip', async () => {
    let serverCalls = 0

    const runtime = createConverterRuntime({
      convertServerScenario: async () => {
        serverCalls += 1

        return {
          job: {
            id: 'job-1',
            uploadId: 'upload-1',
            jobType: 'IMAGE_CONVERT',
            status: 'COMPLETED',
            progressPercent: 100,
            message: 'done',
            errorMessage: null,
            createdAt: new Date().toISOString(),
            startedAt: new Date().toISOString(),
            completedAt: new Date().toISOString(),
            artifacts: [],
          },
          manifestArtifact: {
            id: 'manifest-1',
            kind: 'image-convert-manifest',
            fileName: 'poster-manifest.json',
            mediaType: 'application/json',
            sizeBytes: 64,
            createdAt: new Date().toISOString(),
            downloadPath: '/api/jobs/job-1/artifacts/manifest-1',
          },
          resultArtifact: {
            id: 'result-1',
            kind: 'image-convert-binary',
            fileName: 'poster.jpg',
            mediaType: 'image/jpeg',
            sizeBytes: 128,
            createdAt: new Date().toISOString(),
            downloadPath: '/api/jobs/job-1/artifacts/result-1',
          },
          previewArtifact: {
            id: 'preview-1',
            kind: 'image-convert-preview',
            fileName: 'poster.preview.jpg',
            mediaType: 'image/jpeg',
            sizeBytes: 128,
            createdAt: new Date().toISOString(),
            downloadPath: '/api/jobs/job-1/artifacts/preview-1',
          },
          manifest: {
            operation: 'convert',
            sourceWidth: 1280,
            sourceHeight: 720,
            width: 1280,
            height: 720,
            resultMediaType: 'image/jpeg',
            previewMediaType: 'image/jpeg',
            outputExtension: 'jpg',
            sourceAdapterLabel: 'Backend intake',
            targetAdapterLabel: 'Backend encode',
            runtimeLabel: 'Server raster pipeline',
            warnings: [],
          },
          resultBlob: new Blob(['jpg'], { type: 'image/jpeg' }),
          previewBlob: new Blob(['jpg-preview'], { type: 'image/jpeg' }),
        }
      },
    })

    const prepared = await runtime.inspect(new File(['image'], 'poster.png', { type: 'image/png' }))

    if (!prepared) {
      throw new Error('Expected a prepared source for PNG.')
    }

    const result = await runtime.convert({
      prepared,
      targetExtension: 'jpg',
    })

    expect(serverCalls).toBe(1)
    expect(result.backendJobId).toBe('job-1')
    expect(result.backendRuntimeLabel).toBe('Server raster pipeline')
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
          job: {
            id: 'job-heavy-1',
            uploadId: 'upload-heavy-1',
            jobType: 'IMAGE_CONVERT',
            status: 'COMPLETED',
            progressPercent: 100,
            message: 'done',
            errorMessage: null,
            createdAt: new Date().toISOString(),
            startedAt: new Date().toISOString(),
            completedAt: new Date().toISOString(),
            artifacts: [],
          },
          manifestArtifact: {
            id: 'manifest-heavy-1',
            kind: 'image-convert-manifest',
            fileName: 'capture-manifest.json',
            mediaType: 'application/json',
            sizeBytes: 64,
            createdAt: new Date().toISOString(),
            downloadPath: '/api/jobs/job-heavy-1/artifacts/manifest-heavy-1',
          },
          resultArtifact: {
            id: 'result-heavy-1',
            kind: 'image-convert-binary',
            fileName: 'capture.avif',
            mediaType: 'image/avif',
            sizeBytes: 128,
            createdAt: new Date().toISOString(),
            downloadPath: '/api/jobs/job-heavy-1/artifacts/result-heavy-1',
          },
          previewArtifact: {
            id: 'preview-heavy-1',
            kind: 'image-convert-preview',
            fileName: 'capture.preview.png',
            mediaType: 'image/png',
            sizeBytes: 128,
            createdAt: new Date().toISOString(),
            downloadPath: '/api/jobs/job-heavy-1/artifacts/preview-heavy-1',
          },
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

  it('routes media scenarios through the backend media convert client and keeps separated facts', async () => {
    const runtime = createConverterRuntime({
      convertServerScenario: async ({ target, videoCodec, targetFps, videoBitrateKbps }) => {
        expect(target.extension).toBe('mp4')
        expect(videoCodec).toBe('h264')
        expect(targetFps).toBe(24)
        expect(videoBitrateKbps).toBe(2500)

        return {
          job: {
            id: 'job-media-1',
            uploadId: 'upload-media-1',
            jobType: 'MEDIA_CONVERT',
            status: 'COMPLETED',
            progressPercent: 100,
            message: 'done',
            errorMessage: null,
            createdAt: new Date().toISOString(),
            startedAt: new Date().toISOString(),
            completedAt: new Date().toISOString(),
            artifacts: [],
          },
          manifestArtifact: {
            id: 'manifest-media-1',
            kind: 'media-convert-manifest',
            fileName: 'clip-manifest.json',
            mediaType: 'application/json',
            sizeBytes: 64,
            createdAt: new Date().toISOString(),
            downloadPath: '/api/jobs/job-media-1/artifacts/manifest-media-1',
          },
          resultArtifact: {
            id: 'result-media-1',
            kind: 'media-convert-binary',
            fileName: 'clip.mp4',
            mediaType: 'video/mp4',
            sizeBytes: 128,
            createdAt: new Date().toISOString(),
            downloadPath: '/api/jobs/job-media-1/artifacts/result-media-1',
          },
          previewArtifact: {
            id: 'preview-media-1',
            kind: 'media-convert-preview',
            fileName: 'clip.preview.mp4',
            mediaType: 'video/mp4',
            sizeBytes: 128,
            createdAt: new Date().toISOString(),
            downloadPath: '/api/jobs/job-media-1/artifacts/preview-media-1',
          },
          manifest: {
            uploadId: 'upload-media-1',
            originalFileName: 'clip.mkv',
            sourceExtension: 'mkv',
            targetExtension: 'mp4',
            resultMediaType: 'video/mp4',
            previewMediaType: 'video/mp4',
            previewKind: 'media',
            sourceAdapterLabel: 'FFmpeg video intake',
            targetAdapterLabel: 'MP4 H.264 transcode',
            runtimeLabel: 'FFmpeg video intake -> MP4 H.264 transcode',
            sourceFacts: [
              { label: 'Контейнер', value: 'MKV' },
              { label: 'Video codec', value: 'H.265 / HEVC' },
            ],
            resultFacts: [
              { label: 'Контейнер', value: 'MP4' },
              { label: 'Video codec', value: 'H.264' },
              { label: 'Resolution', value: '1280 x 720' },
            ],
            warnings: ['Resolution изменена отдельно от контейнера: 3840 x 2160 -> 1280 x 720.'],
          },
          resultBlob: new Blob(['mp4'], { type: 'video/mp4' }),
          previewBlob: new Blob(['mp4-preview'], { type: 'video/mp4' }),
        }
      },
    })

    const prepared = await runtime.inspect(new File(['video'], 'clip.mkv', { type: 'video/x-matroska' }))

    if (!prepared) {
      throw new Error('Expected a prepared source for MKV.')
    }

    const result = await runtime.convert({
      prepared,
      targetExtension: 'mp4',
      presetId: 'email-attachment',
      maxWidth: 1280,
      maxHeight: 720,
      videoCodec: 'h264',
      targetFps: 24,
      videoBitrateKbps: 2500,
      audioBitrateKbps: 160,
    })

    expect(result.backendJobId).toBe('job-media-1')
    expect(result.previewMimeType).toBe('video/mp4')
    expect(result.resultFacts).toContainEqual({ label: 'Video codec', value: 'H.264' })
    expect(result.warnings).toContain(
      'Resolution изменена отдельно от контейнера: 3840 x 2160 -> 1280 x 720.',
    )
  })
})
