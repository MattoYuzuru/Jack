import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { resetProcessingCapabilityScopeCache } from '../../../processing/application/processing-client'

import { decodeHeicPreview } from '../viewer-image-preview'

const originalFetch = globalThis.fetch

beforeEach(() => {
  resetProcessingCapabilityScopeCache()
  globalThis.fetch = vi.fn() as typeof fetch
})

afterEach(() => {
  resetProcessingCapabilityScopeCache()
  globalThis.fetch = originalFetch
  vi.clearAllMocks()
})

describe('viewer image preview client', () => {
  it('builds a server-assisted heic preview from backend artifacts', async () => {
    const fetchMock = vi.mocked(globalThis.fetch)
    const progressMessages: string[] = []

    fetchMock
      .mockResolvedValueOnce(
        createJsonResponse({
          scope: 'viewer',
          phase: 'server-capability-matrix',
          jobTypes: [
            {
              jobType: 'METADATA_EXPORT',
              implemented: true,
              detail: 'Backend metadata service is available.',
            },
            {
              jobType: 'IMAGE_CONVERT',
              implemented: true,
              detail: 'Backend image convert service is available.',
            },
          ],
        }),
      )
      .mockResolvedValueOnce(createJsonResponse({ id: 'upload-metadata' }, 201))
      .mockResolvedValueOnce(createJsonResponse({ id: 'job-metadata' }, 202))
      .mockResolvedValueOnce(
        createJsonResponse({
          id: 'job-metadata',
          status: 'COMPLETED',
          progressPercent: 100,
          message: 'Metadata processing готов через backend Image metadata service.',
          errorMessage: null,
          artifacts: [
            {
              id: 'metadata-manifest',
              kind: 'metadata-inspect-manifest',
              fileName: 'metadata-inspect-manifest.json',
              mediaType: 'application/json',
              sizeBytes: 512,
              createdAt: '2026-04-05T15:00:00Z',
              downloadPath: '/api/jobs/job-metadata/artifacts/metadata-manifest',
            },
          ],
        }),
      )
      .mockResolvedValueOnce(
        createJsonResponse({
          operation: 'inspect-image',
          family: 'image',
          imagePayload: {
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
          audioPayload: null,
          warnings: [],
        }),
      )
      .mockResolvedValueOnce(
        createJsonResponse({
          scope: 'viewer',
          phase: 'metadata-service',
          jobTypes: [
            {
              jobType: 'IMAGE_CONVERT',
              implemented: true,
              detail: 'Backend already supports heavy image preview.',
            },
          ],
        }),
      )
      .mockResolvedValueOnce(createJsonResponse({ id: 'upload-image' }, 201))
      .mockResolvedValueOnce(createJsonResponse({ id: 'job-image' }, 202))
      .mockResolvedValueOnce(
        createJsonResponse({
          id: 'job-image',
          status: 'RUNNING',
          progressPercent: 25,
          message: 'Подготавливаю imaging source и raster contract.',
          errorMessage: null,
          artifacts: [],
        }),
      )
      .mockResolvedValueOnce(
        createJsonResponse({
          id: 'job-image',
          status: 'COMPLETED',
          progressPercent: 100,
          message: 'Image processing готов через backend HEIC server rasterization -> Viewer preview PNG.',
          errorMessage: null,
          artifacts: [
            {
              id: 'image-manifest',
              kind: 'image-preview-manifest',
              fileName: 'image-preview-manifest.json',
              mediaType: 'application/json',
              sizeBytes: 512,
              createdAt: '2026-04-05T15:00:00Z',
              downloadPath: '/api/jobs/job-image/artifacts/image-manifest',
            },
            {
              id: 'image-preview',
              kind: 'image-preview-binary',
              fileName: 'capture.preview.png',
              mediaType: 'image/png',
              sizeBytes: 1024,
              createdAt: '2026-04-05T15:00:00Z',
              downloadPath: '/api/jobs/job-image/artifacts/image-preview',
            },
          ],
        }),
      )
      .mockResolvedValueOnce(
        createJsonResponse({
          operation: 'preview',
          sourceAdapterLabel: 'HEIC server rasterization',
          targetAdapterLabel: 'Viewer preview PNG',
          runtimeLabel: 'HEIC server rasterization -> Viewer preview PNG',
          previewMediaType: 'image/png',
          warnings: [],
        }),
      )
      .mockResolvedValueOnce(
        new Response('preview-bytes', {
          status: 200,
          headers: { 'Content-Type': 'image/png' },
        }),
      )

    const file = new File(['heic'], 'capture.heic', { type: 'image/heic' })
    const result = await decodeHeicPreview(new ArrayBuffer(8), {
      file,
      reportProgress(message) {
        progressMessages.push(message)
      },
    })

    expect(fetchMock).toHaveBeenCalledTimes(12)
    expect(fetchMock.mock.calls[0]?.[0]).toBe('http://localhost:8080/api/capabilities/viewer')
    expect(fetchMock.mock.calls[1]?.[0]).toBe('http://localhost:8080/api/uploads')
    expect(fetchMock.mock.calls[2]?.[0]).toBe('http://localhost:8080/api/jobs')

    const jobRequests = fetchMock.mock.calls.filter(
      (call) => call[0] === 'http://localhost:8080/api/jobs' && call[1]?.method === 'POST',
    )

    expect(jobRequests).toHaveLength(2)

    const metadataJobPayload = JSON.parse(String(jobRequests[0]?.[1]?.body))
    expect(metadataJobPayload.parameters).toEqual({
      operation: 'inspect-image',
    })

    const imageJobPayload = JSON.parse(String(jobRequests[1]?.[1]?.body))
    expect(imageJobPayload.parameters).toMatchObject({
      operation: 'preview',
      maxWidth: 4096,
      maxHeight: 4096,
    })

    expect(result.mimeType).toBe('image/png')
    expect(new TextDecoder().decode(result.bytes)).toBe('preview-bytes')
    expect(result.previewLabel).toBe('Backend image preview · HEIC server rasterization')
    expect(result.metadata.summary).toEqual([{ label: 'Камера', value: 'JackCam' }])
    expect(progressMessages).toContain('Подготавливаю imaging source и raster contract.')
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
