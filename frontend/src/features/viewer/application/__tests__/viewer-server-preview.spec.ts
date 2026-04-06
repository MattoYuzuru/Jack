import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { resetProcessingCapabilityScopeCache } from '../../../processing/application/processing-client'
import { resolveServerViewerPreview } from '../viewer-server-preview'

const originalFetch = globalThis.fetch
const originalCreateObjectUrl = URL.createObjectURL
const originalRevokeObjectUrl = URL.revokeObjectURL

beforeEach(() => {
  resetProcessingCapabilityScopeCache()
  globalThis.fetch = vi.fn() as typeof fetch
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
  vi.clearAllMocks()
})

describe('viewer server preview client', () => {
  it('builds a server-assisted image preview from the unified viewer manifest', async () => {
    const fetchMock = vi.mocked(globalThis.fetch)
    const progressMessages: string[] = []

    Object.defineProperty(URL, 'createObjectURL', {
      configurable: true,
      value: vi.fn(() => 'blob:server-image-preview'),
    })

    fetchMock
      .mockResolvedValueOnce(
        createJsonResponse({
          scope: 'viewer',
          phase: 'viewer-backend-first',
          jobTypes: [{ jobType: 'VIEWER_RESOLVE', implemented: true }],
        }),
      )
      .mockResolvedValueOnce(createJsonResponse({ id: 'upload-image' }, 201))
      .mockResolvedValueOnce(createJsonResponse({ id: 'job-image' }, 202))
      .mockResolvedValueOnce(
        createJsonResponse({
          id: 'job-image',
          status: 'COMPLETED',
          progressPercent: 100,
          message: 'Viewer resolve готов через backend Server image preview.',
          errorMessage: null,
          artifacts: [
            {
              id: 'viewer-manifest',
              kind: 'viewer-resolve-manifest',
              fileName: 'viewer-resolve-manifest.json',
              mediaType: 'application/json',
              sizeBytes: 768,
              createdAt: '2026-04-06T10:00:00Z',
              downloadPath: '/api/jobs/job-image/artifacts/viewer-manifest',
            },
          ],
        }),
      )
      .mockResolvedValueOnce(
        createJsonResponse({
          uploadId: 'upload-image',
          originalFileName: 'capture.heic',
          family: 'image',
          kind: 'image',
          previewLabel: 'Server image preview',
          binaryArtifact: {
            kind: 'image-preview-binary',
            fileName: 'capture.preview.png',
            mediaType: 'image/png',
            sizeBytes: 1024,
            downloadPath: '/api/jobs/job-image/artifacts/image-preview',
          },
          imagePayload: {
            width: 3024,
            height: 4032,
            metadata: {
              summary: [{ label: 'Камера', value: 'JackCam' }],
              groups: [],
              editable: {
                description: '',
                artist: '',
                copyright: '',
                capturedAt: '',
              },
              thumbnailDataUrl: null,
            },
            warnings: [],
          },
          documentPayload: null,
          videoPayload: null,
          audioPayload: null,
        }),
      )
      .mockResolvedValueOnce(
        new Response('image-binary', {
          status: 200,
          headers: { 'Content-Type': 'image/png' },
        }),
      )

    const result = await resolveServerViewerPreview(
      new File(['heic'], 'capture.heic', { type: 'image/heic' }),
      (message) => {
        progressMessages.push(message)
      },
    )

    expect(fetchMock).toHaveBeenCalledTimes(6)
    expect(result.kind).toBe('image')
    if (result.kind !== 'image') {
      return
    }

    expect(result.objectUrl).toBe('blob:server-image-preview')
    expect(result.dimensions).toEqual({ width: 3024, height: 4032 })
    expect(result.metadata.summary).toEqual([{ label: 'Камера', value: 'JackCam' }])
    expect(progressMessages).toContain('Загружаю данные для просмотра...')
  })

  it('builds a server-assisted pdf preview from the unified viewer manifest', async () => {
    const fetchMock = vi.mocked(globalThis.fetch)

    Object.defineProperty(URL, 'createObjectURL', {
      configurable: true,
      value: vi.fn(() => 'blob:document-preview'),
    })

    fetchMock
      .mockResolvedValueOnce(
        createJsonResponse({
          scope: 'viewer',
          phase: 'viewer-backend-first',
          jobTypes: [{ jobType: 'VIEWER_RESOLVE', implemented: true }],
        }),
      )
      .mockResolvedValueOnce(createJsonResponse({ id: 'upload-document' }, 201))
      .mockResolvedValueOnce(createJsonResponse({ id: 'job-document' }, 202))
      .mockResolvedValueOnce(
        createJsonResponse({
          id: 'job-document',
          status: 'COMPLETED',
          progressPercent: 100,
          message: 'Viewer resolve готов через backend PDF server preview.',
          errorMessage: null,
          artifacts: [
            {
              id: 'viewer-manifest',
              kind: 'viewer-resolve-manifest',
              fileName: 'viewer-resolve-manifest.json',
              mediaType: 'application/json',
              sizeBytes: 768,
              createdAt: '2026-04-06T10:00:00Z',
              downloadPath: '/api/jobs/job-document/artifacts/viewer-manifest',
            },
          ],
        }),
      )
      .mockResolvedValueOnce(
        createJsonResponse({
          uploadId: 'upload-document',
          originalFileName: 'report.pdf',
          family: 'document',
          kind: 'document',
          previewLabel: 'PDF server preview',
          binaryArtifact: {
            kind: 'document-preview-binary',
            fileName: 'report.preview.pdf',
            mediaType: 'application/pdf',
            sizeBytes: 2048,
            downloadPath: '/api/jobs/job-document/artifacts/document-binary',
          },
          imagePayload: null,
          documentPayload: {
            summary: [
              { label: 'Тип документа', value: 'PDF' },
              { label: 'Страниц', value: '3' },
            ],
            searchableText: 'Alpha beta gamma',
            warnings: ['Backend PDF text extraction completed.'],
            layout: {
              mode: 'pdf',
              pageCount: 3,
              editableDraft: null,
            },
          },
          videoPayload: null,
          audioPayload: null,
        }),
      )
      .mockResolvedValueOnce(
        new Response('pdf-binary', {
          status: 200,
          headers: { 'Content-Type': 'application/pdf' },
        }),
      )

    const result = await resolveServerViewerPreview(
      new File(['pdf'], 'report.pdf', { type: 'application/pdf' }),
    )

    expect(fetchMock).toHaveBeenCalledTimes(6)
    expect(result.kind).toBe('document')
    if (result.kind !== 'document') {
      return
    }

    expect(result.previewLabel).toBe('PDF server preview')
    expect(result.layout.mode).toBe('pdf')
    expect(result.layout.mode === 'pdf' ? result.layout.objectUrl : '').toBe(
      'blob:document-preview',
    )
    expect(result.searchableText).toBe('Alpha beta gamma')
  })

  it('builds a server-assisted audio preview with backend waveform payload', async () => {
    const fetchMock = vi.mocked(globalThis.fetch)

    Object.defineProperty(URL, 'createObjectURL', {
      configurable: true,
      value: vi.fn(() => 'blob:audio-preview'),
    })

    fetchMock
      .mockResolvedValueOnce(
        createJsonResponse({
          scope: 'viewer',
          phase: 'viewer-backend-first',
          jobTypes: [{ jobType: 'VIEWER_RESOLVE', implemented: true }],
        }),
      )
      .mockResolvedValueOnce(createJsonResponse({ id: 'upload-audio' }, 201))
      .mockResolvedValueOnce(createJsonResponse({ id: 'job-audio' }, 202))
      .mockResolvedValueOnce(
        createJsonResponse({
          id: 'job-audio',
          status: 'COMPLETED',
          progressPercent: 100,
          message: 'Viewer resolve готов через backend Server audio preview.',
          errorMessage: null,
          artifacts: [
            {
              id: 'viewer-manifest',
              kind: 'viewer-resolve-manifest',
              fileName: 'viewer-resolve-manifest.json',
              mediaType: 'application/json',
              sizeBytes: 768,
              createdAt: '2026-04-06T10:00:00Z',
              downloadPath: '/api/jobs/job-audio/artifacts/viewer-manifest',
            },
          ],
        }),
      )
      .mockResolvedValueOnce(
        createJsonResponse({
          uploadId: 'upload-audio',
          originalFileName: 'archive.flac',
          family: 'audio',
          kind: 'audio',
          previewLabel: 'Server audio preview',
          binaryArtifact: {
            kind: 'media-preview-binary',
            fileName: 'archive.preview.mp3',
            mediaType: 'audio/mpeg',
            sizeBytes: 512000,
            downloadPath: '/api/jobs/job-audio/artifacts/audio-binary',
          },
          imagePayload: null,
          documentPayload: null,
          videoPayload: null,
          audioPayload: {
            summary: [{ label: 'Playback path', value: 'Backend VIEWER_RESOLVE' }],
            warnings: ['Server preview used.'],
            searchableText: 'Archive track',
            artworkDataUrl: null,
            metadataGroups: [],
            layout: {
              mode: 'native',
              durationSeconds: 8,
              waveform: [0.1, 0.8],
              metadata: {
                mimeType: 'audio/mpeg',
                estimatedBitrateBitsPerSecond: 192000,
                sampleRate: 48000,
                channelCount: 2,
                codec: 'FLAC',
                container: 'FLAC',
                sizeBytes: 512000,
              },
            },
          },
        }),
      )
      .mockResolvedValueOnce(
        new Response('audio-binary', {
          status: 200,
          headers: { 'Content-Type': 'audio/mpeg' },
        }),
      )

    const result = await resolveServerViewerPreview(
      new File(['audio'], 'archive.flac', { type: 'audio/flac' }),
    )

    expect(fetchMock).toHaveBeenCalledTimes(6)
    expect(result.kind).toBe('audio')
    if (result.kind !== 'audio') {
      return
    }

    expect(result.previewLabel).toBe('Server audio preview')
    expect(result.layout.objectUrl).toBe('blob:audio-preview')
    expect(result.layout.waveform).toEqual([0.1, 0.8])
    expect(result.searchableText).toBe('Archive track')
  })
})

function createJsonResponse(payload: unknown, status = 200): Response {
  return new Response(JSON.stringify(payload), {
    status,
    headers: {
      'Content-Type': 'application/json',
    },
  })
}
