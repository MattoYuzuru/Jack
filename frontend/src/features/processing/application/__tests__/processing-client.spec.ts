import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  awaitProcessingJob,
  cancelProcessingJob,
  getPlatformCapabilityMatrix,
  ProcessingJobCancelledError,
  resetProcessingCapabilityScopeCache,
} from '../processing-client'
import { createPlatformCapabilityScopeFixture } from './capability-matrix.fixtures'

const originalFetch = globalThis.fetch

describe('processing client', () => {
  afterEach(() => {
    globalThis.fetch = originalFetch
    resetProcessingCapabilityScopeCache()
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
})
