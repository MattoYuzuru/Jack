import { describe, expect, it, vi } from 'vitest'
import { createProcessingTaskController } from '../processing-task-controller'

describe('processing task controller', () => {
  it('aborts superseded revisions and cancels their server job', async () => {
    const cancelJob = vi.fn().mockResolvedValue(undefined)
    const controller = createProcessingTaskController<{ value: string }>({ cancelJob })
    const first = controller.begin({ value: 'first' })
    controller.registerJob(first, 'job-1')

    const second = controller.begin({ value: 'second' })
    await vi.waitFor(() => expect(cancelJob).toHaveBeenCalledWith('job-1'))

    expect(first.signal.aborted).toBe(true)
    expect(controller.isCurrent(first)).toBe(false)
    expect(controller.isCurrent(second)).toBe(true)
  })

  it('retries an immutable snapshot and disposes active work', () => {
    const controller = createProcessingTaskController<{ value: string }>({
      cloneSnapshot: (snapshot) => ({ ...snapshot }),
    })
    const source = { value: 'stable' }
    controller.begin(source)
    source.value = 'mutated'

    const retry = controller.retry()
    expect(retry?.snapshot.value).toBe('stable')

    controller.dispose()
    expect(retry?.signal.aborted).toBe(true)
    expect(controller.getLastSnapshot()).toBeNull()
  })
})
