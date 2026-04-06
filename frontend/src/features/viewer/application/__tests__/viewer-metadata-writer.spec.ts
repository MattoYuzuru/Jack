import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { resetProcessingCapabilityScopeCache } from '../../../processing/application/processing-client'
import { canEmbedMetadata, exportViewerMetadata } from '../viewer-metadata-writer'

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

describe('viewer metadata writer', () => {
  it('detects when metadata can be embedded directly into jpeg files', () => {
    expect(canEmbedMetadata('cover.jpg')).toBe(true)
    expect(canEmbedMetadata('cover.jpeg')).toBe(true)
    expect(canEmbedMetadata('cover.png')).toBe(false)
  })

  it('exports metadata through backend artifacts', async () => {
    const fetchMock = vi.mocked(globalThis.fetch)

    fetchMock
      .mockResolvedValueOnce(
        createJsonResponse({
          scope: 'viewer',
          phase: 'metadata-service',
          jobTypes: [
            {
              jobType: 'METADATA_EXPORT',
              implemented: true,
              detail: 'Backend metadata service is available.',
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
          message: 'Metadata processing готов через backend Metadata export service.',
          errorMessage: null,
          artifacts: [
            {
              id: 'metadata-manifest',
              kind: 'metadata-export-manifest',
              fileName: 'metadata-export-manifest.json',
              mediaType: 'application/json',
              sizeBytes: 256,
              createdAt: '2026-04-05T15:00:00Z',
              downloadPath: '/api/jobs/job-metadata/artifacts/metadata-manifest',
            },
            {
              id: 'metadata-binary',
              kind: 'metadata-export-binary',
              fileName: 'frame.png.jack-metadata.json',
              mediaType: 'application/json',
              sizeBytes: 512,
              createdAt: '2026-04-05T15:00:00Z',
              downloadPath: '/api/jobs/job-metadata/artifacts/metadata-binary',
            },
          ],
        }),
      )
      .mockResolvedValueOnce(
        createJsonResponse({
          mode: 'json-sidecar',
          fileName: 'frame.png.jack-metadata.json',
          warnings: [],
        }),
      )
      .mockResolvedValueOnce(
        new Response('metadata-sidecar', {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        }),
      )

    const result = await exportViewerMetadata(
      new File(['png'], 'frame.png', { type: 'image/png' }),
      {
        description: 'Product render',
        artist: 'Jack',
        copyright: 'Jack Studio',
        capturedAt: '2026-04-04T15:30',
      },
    )

    expect(fetchMock).toHaveBeenCalledTimes(6)
    expect(result.mode).toBe('json-sidecar')
    expect(result.fileName).toBe('frame.png.jack-metadata.json')
    expect(await result.blob.text()).toBe('metadata-sidecar')

    const jobPayload = JSON.parse(String(fetchMock.mock.calls[2]?.[1]?.body))
    expect(jobPayload.parameters).toEqual({
      operation: 'export-image',
      metadata: {
        description: 'Product render',
        artist: 'Jack',
        copyright: 'Jack Studio',
        capturedAt: '2026-04-04T15:30',
      },
    })
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
