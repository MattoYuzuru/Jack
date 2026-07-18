import { cancelProcessingJob } from './processing-client'

export interface ProcessingTaskHandle<TSnapshot> {
  readonly revision: number
  readonly snapshot: Readonly<TSnapshot>
  readonly signal: AbortSignal
}

interface ProcessingTaskControllerOptions<TSnapshot> {
  cloneSnapshot?: (snapshot: TSnapshot) => TSnapshot
  cancelJob?: (jobId: string) => Promise<unknown>
}

export function createProcessingTaskController<TSnapshot>(
  options: ProcessingTaskControllerOptions<TSnapshot> = {},
) {
  const cloneSnapshot =
    options.cloneSnapshot ?? ((snapshot: TSnapshot) => structuredClone(snapshot))
  const cancelJob = options.cancelJob ?? cancelProcessingJob
  let revision = 0
  let active:
    | {
        handle: ProcessingTaskHandle<TSnapshot>
        controller: AbortController
        jobId: string | null
      }
    | undefined
  let lastSnapshot: Readonly<TSnapshot> | null = null

  function begin(snapshot: TSnapshot): ProcessingTaskHandle<TSnapshot> {
    cancelActive()
    const controller = new AbortController()
    const immutableSnapshot = Object.freeze(cloneSnapshot(snapshot))
    const handle = Object.freeze({
      revision: (revision += 1),
      snapshot: immutableSnapshot,
      signal: controller.signal,
    })
    active = { handle, controller, jobId: null }
    lastSnapshot = immutableSnapshot
    return handle
  }

  function registerJob(handle: ProcessingTaskHandle<TSnapshot>, jobId: string): void {
    if (isCurrent(handle)) {
      active!.jobId = jobId
    }
  }

  function isCurrent(handle: ProcessingTaskHandle<TSnapshot>): boolean {
    return active?.handle === handle && !handle.signal.aborted
  }

  function complete(handle: ProcessingTaskHandle<TSnapshot>): void {
    if (active?.handle === handle) {
      active = undefined
    }
  }

  function cancelActive(): void {
    if (!active) {
      return
    }

    const current = active
    active = undefined
    current.controller.abort()
    if (current.jobId) {
      // Отмена UI не должна оставлять тяжёлую серверную задачу в фоне. Ошибка
      // DELETE не меняет локальную отмену и будет отражена durable job state.
      void cancelJob(current.jobId).catch(() => undefined)
    }
  }

  function retry(): ProcessingTaskHandle<TSnapshot> | null {
    return lastSnapshot ? begin(lastSnapshot as TSnapshot) : null
  }

  function dispose(): void {
    cancelActive()
    lastSnapshot = null
  }

  return {
    begin,
    registerJob,
    isCurrent,
    complete,
    cancelActive,
    retry,
    dispose,
    getLastSnapshot: () => lastSnapshot,
  }
}
