import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { decodeHeicPreview } from '../viewer-image-preview'

const originalFetch = globalThis.fetch

beforeEach(() => {
  globalThis.fetch = vi.fn() as typeof fetch
})

afterEach(() => {
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
          phase: 'imaging-foundation',
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

    expect(fetchMock).toHaveBeenCalledTimes(7)
    expect(fetchMock.mock.calls[0]?.[0]).toBe('http://localhost:8080/api/capabilities/viewer')
    expect(fetchMock.mock.calls[1]?.[0]).toBe('http://localhost:8080/api/uploads')
    expect(fetchMock.mock.calls[2]?.[0]).toBe('http://localhost:8080/api/jobs')

    const jobPayload = JSON.parse(String(fetchMock.mock.calls[2]?.[1]?.body))
    expect(jobPayload.parameters).toMatchObject({
      operation: 'preview',
      maxWidth: 4096,
      maxHeight: 4096,
    })

    expect(result.mimeType).toBe('image/png')
    expect(new TextDecoder().decode(result.bytes)).toBe('preview-bytes')
    expect(result.previewLabel).toBe('Backend image preview · HEIC server rasterization')
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
