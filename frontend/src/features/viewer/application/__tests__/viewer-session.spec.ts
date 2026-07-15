import { afterEach, describe, expect, it, vi } from 'vitest'
import { createViewerSession, type ViewerSessionSnapshot } from '../viewer-session'
import {
  ViewerResolutionCancelledError,
  type ViewerResolvedEntry,
  type ViewerResolvedUnknown,
  type ViewerRuntime,
} from '../viewer-runtime'

const originalRevokeObjectUrl = URL.revokeObjectURL

afterEach(() => {
  Object.defineProperty(URL, 'revokeObjectURL', {
    configurable: true,
    value: originalRevokeObjectUrl,
  })
})

describe('viewer session', () => {
  it('keeps the newest selection and releases a late stale result', async () => {
    const first = deferred<ViewerResolvedEntry>()
    const second = deferred<ViewerResolvedEntry>()
    const releaseEntry = vi.fn()
    const snapshots: ViewerSessionSnapshot[] = []
    const runtime: ViewerRuntime = {
      resolve: vi
        .fn()
        .mockImplementationOnce(() => first.promise)
        .mockImplementationOnce(() => second.promise),
    }
    const session = createViewerSession({
      runtime,
      releaseEntry,
      onChange: (snapshot) => snapshots.push(snapshot),
    })

    const firstSelection = createUnknownEntry('first.txt')
    const secondSelection = createUnknownEntry('second.txt')
    const firstRequest = session.select(firstSelection.file)
    await Promise.resolve()
    const secondRequest = session.select(secondSelection.file)
    await Promise.resolve()

    second.resolve(secondSelection)
    await secondRequest
    first.resolve(firstSelection)
    await firstRequest

    expect(session.getSnapshot().selection).toBe(secondSelection)
    expect(session.getSnapshot().status).toBe('ready')
    expect(releaseEntry).toHaveBeenCalledWith(firstSelection)
    expect(snapshots[snapshots.length - 1]?.selection).toBe(secondSelection)
  })

  it('aborts the active resolve without presenting cancellation as an error', async () => {
    let capturedSignal: AbortSignal | undefined
    const resolve: ViewerRuntime['resolve'] = vi.fn((_file, options) => {
      capturedSignal = options?.signal
      return new Promise<ViewerResolvedEntry>((_resolve, reject) => {
        options?.signal?.addEventListener('abort', () => {
          reject(new ViewerResolutionCancelledError())
        })
      })
    })
    const runtime: ViewerRuntime = { resolve }
    const session = createViewerSession({ runtime, onChange: vi.fn() })

    const request = session.select(new File(['first'], 'first.txt', { type: 'text/plain' }))
    await Promise.resolve()
    session.clear()
    await request

    expect(capturedSignal?.aborted).toBe(true)
    expect(session.getSnapshot()).toMatchObject({
      status: 'idle',
      selection: null,
      errorMessage: '',
    })
  })

  it('revokes the current object URL when a new file replaces it', async () => {
    const revokeObjectUrl = vi.fn()
    Object.defineProperty(URL, 'revokeObjectURL', {
      configurable: true,
      value: revokeObjectUrl,
    })

    const imageSelection = {
      kind: 'image',
      objectUrl: 'blob:first-preview',
    } as ViewerResolvedEntry
    const pending = deferred<ViewerResolvedEntry>()
    const runtime: ViewerRuntime = {
      resolve: vi
        .fn()
        .mockResolvedValueOnce(imageSelection)
        .mockImplementationOnce(() => pending.promise),
    }
    const session = createViewerSession({ runtime, onChange: vi.fn() })

    await session.select(new File(['image'], 'first.png', { type: 'image/png' }))
    const replacement = session.select(new File(['next'], 'next.txt', { type: 'text/plain' }))

    expect(revokeObjectUrl).toHaveBeenCalledWith('blob:first-preview')

    session.clear()
    pending.resolve(createUnknownEntry('next.txt'))
    await replacement
  })

  it('releases the ready selection on dispose without publishing another state', async () => {
    const releaseEntry = vi.fn()
    const onChange = vi.fn()
    const entry = createUnknownEntry('ready.txt')
    const session = createViewerSession({
      runtime: { resolve: vi.fn().mockResolvedValue(entry) },
      releaseEntry,
      onChange,
    })

    await session.select(entry.file)
    const publishedStates = onChange.mock.calls.length
    session.dispose()

    expect(releaseEntry).toHaveBeenCalledWith(entry)
    expect(onChange).toHaveBeenCalledTimes(publishedStates)
    expect(session.getSnapshot().status).toBe('idle')
  })
})

function createUnknownEntry(name: string): ViewerResolvedUnknown {
  return {
    kind: 'unknown',
    file: new File([name], name, { type: 'text/plain' }),
    extension: 'txt',
    headline: 'Fixture',
    detail: 'Fixture',
    nextStep: 'Fixture',
  }
}

function deferred<T>(): {
  promise: Promise<T>
  resolve: (value: T) => void
} {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((promiseResolve) => {
    resolve = promiseResolve
  })

  return { promise, resolve }
}
