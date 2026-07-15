import {
  releaseViewerEntry,
  ViewerResolutionCancelledError,
  type ViewerResolvedEntry,
  type ViewerRuntime,
} from './viewer-runtime'

export type ViewerSessionStatus = 'idle' | 'resolving' | 'ready' | 'error'

export interface ViewerSessionSnapshot {
  status: ViewerSessionStatus
  selection: ViewerResolvedEntry | null
  loadingMessage: string
  errorMessage: string
}

export interface ViewerSessionDependencies {
  runtime: ViewerRuntime
  prepare?: () => Promise<void>
  describeLoading?: (file: File) => Promise<string>
  releaseEntry?: (entry: ViewerResolvedEntry | null) => void
  onChange: (snapshot: ViewerSessionSnapshot) => void
}

export interface ViewerSession {
  select(file: File): Promise<void>
  clear(): void
  dispose(): void
  getSnapshot(): ViewerSessionSnapshot
}

const DEFAULT_LOADING_MESSAGE = 'Подготавливаю просмотр...'

export function createViewerSession(dependencies: ViewerSessionDependencies): ViewerSession {
  const releaseEntry = dependencies.releaseEntry ?? releaseViewerEntry
  let revision = 0
  let activeController: AbortController | null = null
  let disposed = false
  let snapshot: ViewerSessionSnapshot = {
    status: 'idle',
    selection: null,
    loadingMessage: DEFAULT_LOADING_MESSAGE,
    errorMessage: '',
  }

  function publish(next: ViewerSessionSnapshot): void {
    snapshot = next
    if (!disposed) {
      dependencies.onChange(snapshot)
    }
  }

  function cancelActiveRequest(): void {
    activeController?.abort()
    activeController = null
  }

  function releaseCurrentSelection(): void {
    releaseEntry(snapshot.selection)
  }

  function reset(publishChange: boolean): void {
    revision += 1
    cancelActiveRequest()
    releaseCurrentSelection()
    const idleSnapshot: ViewerSessionSnapshot = {
      status: 'idle',
      selection: null,
      loadingMessage: DEFAULT_LOADING_MESSAGE,
      errorMessage: '',
    }

    if (publishChange) {
      publish(idleSnapshot)
    } else {
      snapshot = idleSnapshot
    }
  }

  async function select(file: File): Promise<void> {
    const currentRevision = ++revision
    cancelActiveRequest()
    releaseCurrentSelection()

    const controller = new AbortController()
    activeController = controller
    publish({
      status: 'resolving',
      selection: null,
      loadingMessage: DEFAULT_LOADING_MESSAGE,
      errorMessage: '',
    })

    // Подсказка вычисляется отдельно от тяжёлого resolve, но revision не даёт
    // позднему результату от предыдущего файла перезаписать актуальный статус.
    void dependencies
      .describeLoading?.(file)
      .then((message) => {
        if (currentRevision === revision && snapshot.status === 'resolving') {
          publish({ ...snapshot, loadingMessage: message })
        }
      })
      .catch(() => undefined)

    try {
      await dependencies.prepare?.()
      if (currentRevision !== revision || controller.signal.aborted) {
        return
      }

      const resolvedEntry = await dependencies.runtime.resolve(file, {
        signal: controller.signal,
        onProgress(message) {
          if (currentRevision === revision && snapshot.status === 'resolving') {
            publish({ ...snapshot, loadingMessage: message })
          }
        },
      })

      // Некоторые browser API нельзя физически отменить. Их поздний результат
      // обязательно освобождаем, даже если runtime проигнорировал AbortSignal.
      if (currentRevision !== revision || controller.signal.aborted || disposed) {
        releaseEntry(resolvedEntry)
        return
      }

      publish({
        status: 'ready',
        selection: resolvedEntry,
        loadingMessage: snapshot.loadingMessage,
        errorMessage: '',
      })
    } catch (error) {
      if (currentRevision !== revision || disposed) {
        return
      }

      if (error instanceof ViewerResolutionCancelledError || controller.signal.aborted) {
        publish({
          status: 'idle',
          selection: null,
          loadingMessage: DEFAULT_LOADING_MESSAGE,
          errorMessage: '',
        })
        return
      }

      publish({
        status: 'error',
        selection: null,
        loadingMessage: snapshot.loadingMessage,
        errorMessage:
          error instanceof Error
            ? error.message
            : 'Не удалось подготовить просмотр для выбранного файла.',
      })
    } finally {
      if (currentRevision === revision && activeController === controller) {
        activeController = null
      }
    }
  }

  return {
    select,
    clear() {
      reset(true)
    },
    dispose() {
      disposed = true
      reset(false)
    },
    getSnapshot() {
      return snapshot
    },
  }
}
