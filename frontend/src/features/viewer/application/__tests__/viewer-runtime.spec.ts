import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createViewerCapabilityScopeFixture } from '../../../processing/application/__tests__/capability-matrix.fixtures'
import { resetProcessingCapabilityScopeCache } from '../../../processing/application/processing-client'
import { createEmptyMetadataPayload } from '../viewer-metadata'
import { createViewerRuntime, releaseViewerEntry } from '../viewer-runtime'

const originalFetch = globalThis.fetch
const originalCreateObjectUrl = URL.createObjectURL
const originalRevokeObjectUrl = URL.revokeObjectURL

beforeEach(() => {
  resetProcessingCapabilityScopeCache()
  globalThis.fetch = vi.fn().mockResolvedValue(
    new Response(JSON.stringify(createViewerCapabilityScopeFixture()), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    }),
  ) as typeof fetch
})

afterEach(() => {
  resetProcessingCapabilityScopeCache()
  globalThis.fetch = originalFetch
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

    const runtime = createViewerRuntime({
      inspectNativeImage: async () => ({ width: 1440, height: 900 }),
      loadNativeMetadata: async () => ({
        ...createEmptyMetadataPayload(),
        summary: [{ label: 'Камера', value: 'JackCam' }],
      }),
    })

    const result = await runtime.resolve(new File(['image'], 'poster.png', { type: 'image/png' }))

    if (result.kind !== 'image') {
      throw new Error('Expected a native image preview result.')
    }

    expect(result.objectUrl).toBe('blob:preview')
    expect(result.dimensions).toEqual({ width: 1440, height: 900 })
    expect(result.format.extension).toBe('png')
    expect(result.metadata.summary).toEqual([{ label: 'Камера', value: 'JackCam' }])
  })

  it('routes server-assisted image formats through the unified server viewer adapter', async () => {
    const runtime = createViewerRuntime({
      buildServerViewer: async () => ({
        kind: 'image',
        objectUrl: 'blob:server-image-preview',
        dimensions: { width: 3024, height: 4032 },
        metadata: {
          ...createEmptyMetadataPayload(),
          summary: [{ label: 'Тип файла', value: 'HEIC' }],
        },
        previewLabel: 'Server image preview',
      }),
    })

    const result = await runtime.resolve(
      new File(['image'], 'capture.heic', { type: 'image/heic' }),
    )

    if (result.kind !== 'image') {
      throw new Error('Expected a server image preview result.')
    }

    expect(result.format.extension).toBe('heic')
    expect(result.previewLabel).toBe('Server image preview')
    expect(result.metadata.summary).toEqual([{ label: 'Тип файла', value: 'HEIC' }])
  })

  it('routes document formats through the unified server viewer adapter', async () => {
    const revokeObjectUrl = vi.fn()

    Object.defineProperty(URL, 'revokeObjectURL', {
      configurable: true,
      value: revokeObjectUrl,
    })

    const runtime = createViewerRuntime({
      buildServerViewer: async () => ({
        kind: 'document',
        summary: [{ label: 'Страниц', value: '3' }],
        searchableText: 'Alpha beta gamma',
        warnings: ['Search layer ограничен первыми страницами.'],
        layout: {
          mode: 'pdf',
          objectUrl: 'blob:pdf-preview',
          pageCount: 3,
        },
        previewLabel: 'Server document preview',
      }),
    })

    const result = await runtime.resolve(new File(['pdf'], 'deck.pdf', { type: 'application/pdf' }))

    if (result.kind !== 'document') {
      throw new Error('Expected a document preview result.')
    }

    expect(result.layout.mode).toBe('pdf')
    expect(result.previewLabel).toBe('Server document preview')
    expect(result.summary).toEqual([{ label: 'Страниц', value: '3' }])
    expect(result.searchableText).toContain('beta')

    releaseViewerEntry(result)

    expect(revokeObjectUrl).toHaveBeenCalledWith('blob:pdf-preview')
  })

  it('builds a video preview for browser-native formats', async () => {
    const runtime = createViewerRuntime({
      buildNativeVideo: async () => ({
        summary: [{ label: 'Длительность', value: '00:12' }],
        warnings: [],
        layout: {
          mode: 'native',
          objectUrl: 'blob:video-preview',
          durationSeconds: 12,
          width: 1920,
          height: 1080,
          metadata: {
            mimeType: 'video/mp4',
            aspectRatio: '16:9',
            orientation: 'Landscape',
            estimatedBitrateBitsPerSecond: 4_200_000,
            sizeBytes: 6_300_000,
          },
        },
        previewLabel: 'Browser video',
      }),
    })

    const result = await runtime.resolve(new File(['video'], 'clip.mp4', { type: 'video/mp4' }))

    if (result.kind !== 'video') {
      throw new Error('Expected a video preview result.')
    }

    expect(result.previewLabel).toBe('Browser video')
    expect(result.layout.durationSeconds).toBe(12)
    expect(result.layout.width).toBe(1920)
  })

  it('routes legacy video containers through the unified server viewer adapter', async () => {
    const runtime = createViewerRuntime({
      buildServerViewer: async () => ({
        kind: 'video',
        summary: [{ label: 'Playback path', value: 'Backend VIEWER_RESOLVE' }],
        warnings: ['Server preview used.'],
        layout: {
          mode: 'native',
          objectUrl: 'blob:legacy-video-preview',
          durationSeconds: 18,
          width: 1280,
          height: 720,
          metadata: {
            mimeType: 'video/mp4',
            aspectRatio: '16:9',
            orientation: 'Landscape',
            estimatedBitrateBitsPerSecond: 3_600_000,
            sizeBytes: 8_100_000,
          },
        },
        previewLabel: 'Server video preview',
      }),
    })

    const result = await runtime.resolve(
      new File(['video'], 'archive.mkv', {
        type: 'video/x-matroska',
      }),
    )

    if (result.kind !== 'video') {
      throw new Error('Expected a server video preview result.')
    }

    expect(result.previewLabel).toBe('Server video preview')
    expect(result.format.extension).toBe('mkv')
    expect(result.summary).toEqual([{ label: 'Playback path', value: 'Backend VIEWER_RESOLVE' }])
  })

  it('builds an audio preview for browser-native formats', async () => {
    const revokeObjectUrl = vi.fn()

    Object.defineProperty(URL, 'revokeObjectURL', {
      configurable: true,
      value: revokeObjectUrl,
    })

    const runtime = createViewerRuntime({
      buildNativeAudio: async () => ({
        summary: [{ label: 'Длительность', value: '03:10' }],
        warnings: [],
        searchableText: 'Jack Viewer Demo',
        artworkDataUrl: 'data:image/png;base64,abc',
        metadataGroups: [],
        layout: {
          mode: 'native',
          objectUrl: 'blob:audio-preview',
          durationSeconds: 190,
          waveform: [0.2, 0.6, 1],
          metadata: {
            mimeType: 'audio/mpeg',
            estimatedBitrateBitsPerSecond: 192_000,
            sampleRate: 44_100,
            channelCount: 2,
            codec: 'MPEG Layer III',
            container: 'MPEG',
            sizeBytes: 4_800_000,
          },
        },
        previewLabel: 'Browser audio',
      }),
    })

    const result = await runtime.resolve(new File(['audio'], 'song.mp3', { type: 'audio/mpeg' }))

    if (result.kind !== 'audio') {
      throw new Error('Expected an audio preview result.')
    }

    expect(result.previewLabel).toBe('Browser audio')
    expect(result.layout.durationSeconds).toBe(190)
    expect(result.layout.waveform).toEqual([0.2, 0.6, 1])

    releaseViewerEntry(result)

    expect(revokeObjectUrl).toHaveBeenCalledWith('blob:audio-preview')
  })

  it('routes legacy audio containers through the unified server viewer adapter', async () => {
    const runtime = createViewerRuntime({
      buildServerViewer: async () => ({
        kind: 'audio',
        summary: [{ label: 'Playback path', value: 'Backend VIEWER_RESOLVE' }],
        warnings: ['Server preview used.'],
        searchableText: 'Lossless archive',
        artworkDataUrl: null,
        metadataGroups: [],
        layout: {
          mode: 'native',
          objectUrl: 'blob:legacy-audio-preview',
          durationSeconds: 242,
          waveform: [0.3, 0.9],
          metadata: {
            mimeType: 'audio/mpeg',
            estimatedBitrateBitsPerSecond: 768_000,
            sampleRate: 48_000,
            channelCount: 2,
            codec: 'FLAC',
            container: 'FLAC',
            sizeBytes: 18_000_000,
          },
        },
        previewLabel: 'Server audio preview',
      }),
    })

    const result = await runtime.resolve(
      new File(['audio'], 'archive.flac', { type: 'audio/flac' }),
    )

    if (result.kind !== 'audio') {
      throw new Error('Expected a server audio preview result.')
    }

    expect(result.previewLabel).toBe('Server audio preview')
    expect(result.format.extension).toBe('flac')
    expect(result.summary).toEqual([{ label: 'Playback path', value: 'Backend VIEWER_RESOLVE' }])
  })

  it('returns an unknown result when backend marks the format unavailable', async () => {
    const fetchMock = vi.mocked(globalThis.fetch)
    const capabilityFixture = createViewerCapabilityScopeFixture()
    const viewerFormats = (capabilityFixture.viewerMatrix?.formats ?? []) as Array<{
      extension: string
      available: boolean
      availabilityDetail: string | null
    }>
    const wmvFormat = viewerFormats.find(
      (definition) => definition.extension === 'wmv',
    )

    if (!wmvFormat) {
      throw new Error('WMV capability fixture is required for this test.')
    }

    wmvFormat.available = false
    wmvFormat.availabilityDetail = 'MEDIA_PREVIEW временно выключен на backend.'

    fetchMock.mockResolvedValue(
      new Response(JSON.stringify(capabilityFixture), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )

    const runtime = createViewerRuntime()
    const result = await runtime.resolve(new File(['video'], 'clip.wmv', { type: 'video/x-ms-wmv' }))

    expect(result.kind).toBe('unknown')
    if (result.kind !== 'unknown') {
      return
    }

    expect(result.detail).toContain('MEDIA_PREVIEW временно выключен')
  })
})
