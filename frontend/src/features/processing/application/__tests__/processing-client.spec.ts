import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  awaitProcessingJob,
  cancelProcessingJob,
  getPlatformCapabilityMatrix,
  ProcessingJobAbortedError,
  ProcessingJobCancelledError,
  resetProcessingCapabilityScopeCache,
  runProcessingJob,
} from '../processing-client'
import {
  createPlatformCapabilityScopeFixture,
  createViewerCapabilityScopeFixture,
} from './capability-matrix.fixtures'

const originalFetch = globalThis.fetch

describe('processing client', () => {
  afterEach(() => {
    globalThis.fetch = originalFetch
    resetProcessingCapabilityScopeCache()
    vi.unstubAllEnvs()
  })

  it('throws ProcessingJobCancelledError when polling sees a cancelled job', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          id: 'job-1',
          uploadId: 'upload-1',
          jobType: 'IMAGE_CONVERT',
          status: 'CANCELLED',
          progressPercent: 35,
          message: 'Job отменён пользователем.',
          errorMessage: null,
          createdAt: new Date().toISOString(),
          startedAt: new Date().toISOString(),
          completedAt: new Date().toISOString(),
          artifacts: [],
        }),
        {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        },
      ),
    ) as typeof fetch

    await expect(awaitProcessingJob('job-1', { maxAttempts: 1 })).rejects.toBeInstanceOf(
      ProcessingJobCancelledError,
    )
  })

  it('sends DELETE request when cancelling a job', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          id: 'job-2',
          uploadId: 'upload-2',
          jobType: 'IMAGE_CONVERT',
          status: 'CANCELLED',
          progressPercent: 25,
          message: 'Job отменён пользователем.',
          errorMessage: null,
          createdAt: new Date().toISOString(),
          startedAt: new Date().toISOString(),
          completedAt: new Date().toISOString(),
          artifacts: [],
        }),
        {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        },
      ),
    )
    globalThis.fetch = fetchMock as typeof fetch

    await cancelProcessingJob('job-2')

    expect(fetchMock).toHaveBeenCalledWith(
      'http://localhost:8080/api/jobs/job-2',
      expect.objectContaining({ method: 'DELETE' }),
    )
  })

  it('cancels a created job when the caller aborts polling', async () => {
    const controller = new AbortController()
    const runningJob = {
      id: 'job-4',
      uploadId: 'upload-4',
      jobType: 'VIEWER_RESOLVE',
      status: 'RUNNING',
      progressPercent: 20,
      message: 'Готовлю preview.',
      errorMessage: null,
      createdAt: new Date().toISOString(),
      startedAt: new Date().toISOString(),
      completedAt: null,
      artifacts: [],
    }
    const fetchMock = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input)

      if (url.endsWith('/api/capabilities/viewer')) {
        return Promise.resolve(
          new Response(JSON.stringify(createViewerCapabilityScopeFixture()), {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
          }),
        )
      }

      if (url.endsWith('/api/uploads')) {
        return Promise.resolve(
          new Response(JSON.stringify({ id: 'upload-4' }), {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
          }),
        )
      }

      if (url.endsWith('/api/jobs') && init?.method === 'POST') {
        return Promise.resolve(
          new Response(JSON.stringify(runningJob), {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
          }),
        )
      }

      if (url.endsWith('/api/jobs/job-4') && init?.method === 'DELETE') {
        return Promise.resolve(
          new Response(JSON.stringify({ ...runningJob, status: 'CANCELLED' }), {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
          }),
        )
      }

      controller.abort()
      return Promise.reject(new DOMException('Aborted', 'AbortError'))
    })
    globalThis.fetch = fetchMock as typeof fetch

    await expect(
      runProcessingJob({
        scope: 'viewer',
        file: new File(['viewer'], 'preview.pdf', { type: 'application/pdf' }),
        jobType: 'VIEWER_RESOLVE',
        maxAttempts: 2,
        signal: controller.signal,
      }),
    ).rejects.toBeInstanceOf(ProcessingJobAbortedError)

    await vi.waitFor(() => {
      expect(fetchMock).toHaveBeenCalledWith(
        'http://localhost:8080/api/jobs/job-4',
        expect.objectContaining({ method: 'DELETE' }),
      )
    })
  })

  it('loads platform matrix from backend capability scope', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(
      new Response(JSON.stringify(createPlatformCapabilityScopeFixture()), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    ) as typeof fetch

    const matrix = await getPlatformCapabilityMatrix()

    expect(matrix.modules).toHaveLength(6)
    expect(matrix.modules[0]?.id).toBe('compression')
  })

  it('resolves relative production api base url against current origin', async () => {
    vi.stubEnv('VITE_API_BASE_URL', '/api')

    const fetchMock = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          id: 'job-3',
          uploadId: 'upload-3',
          jobType: 'IMAGE_CONVERT',
          status: 'CANCELLED',
          progressPercent: 25,
          message: 'Job отменён пользователем.',
          errorMessage: null,
          createdAt: new Date().toISOString(),
          startedAt: new Date().toISOString(),
          completedAt: new Date().toISOString(),
          artifacts: [],
        }),
        {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        },
      ),
    )
    globalThis.fetch = fetchMock as typeof fetch

    await cancelProcessingJob('job-3')

    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringMatching(/\/api\/jobs\/job-3$/),
      expect.objectContaining({ method: 'DELETE' }),
    )
  })
})
