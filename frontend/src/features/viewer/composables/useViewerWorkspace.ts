import { computed, onBeforeUnmount, ref, shallowRef } from 'vue'
import {
  getViewerCapabilityMatrix,
  resolveViewerFormat,
  type ViewerFormatDefinition,
} from '../domain/viewer-registry'
import {
  createViewerRuntime,
  releaseViewerEntry,
  type ViewerResolvedEntry,
} from '../application/viewer-runtime'

const viewerRuntime = createViewerRuntime()

export function useViewerWorkspace() {
  const selection = shallowRef<ViewerResolvedEntry | null>(null)
  const isLoading = ref(false)
  const errorMessage = ref('')
  const loadingMessage = ref('Подготавливаю просмотр...')
  const viewerAcceptAttribute = ref('')
  const imageFormats = ref<ViewerFormatDefinition[]>([])
  const documentFormats = ref<ViewerFormatDefinition[]>([])
  const mediaFormats = ref<ViewerFormatDefinition[]>([])
  const audioFormats = ref<ViewerFormatDefinition[]>([])
  const zoom = ref(1)
  const rotation = ref(0)
  let activeSelectionRequest = 0
  let capabilityMatrixRequest: Promise<void> | null = null

  async function ensureCapabilityMatrix(): Promise<void> {
    if (!capabilityMatrixRequest) {
      capabilityMatrixRequest = loadCapabilityMatrix().catch((error) => {
        capabilityMatrixRequest = null
        throw error
      })
    }

    return capabilityMatrixRequest
  }

  async function loadCapabilityMatrix(): Promise<void> {
    const matrix = await getViewerCapabilityMatrix()

    viewerAcceptAttribute.value = matrix.acceptAttribute
    imageFormats.value = matrix.formats.filter((definition) => definition.family === 'image')
    documentFormats.value = matrix.formats.filter((definition) => definition.family === 'document')
    mediaFormats.value = matrix.formats.filter((definition) => definition.family === 'media')
    audioFormats.value = matrix.formats.filter((definition) => definition.family === 'audio')
  }

  void ensureCapabilityMatrix().catch(() => undefined)

  function releaseSelection() {
    releaseViewerEntry(selection.value)
  }

  function resetViewportTransform() {
    zoom.value = 1
    rotation.value = 0
  }

  async function selectFile(file: File) {
    const selectionRequest = ++activeSelectionRequest
    releaseSelection()
    selection.value = null
    errorMessage.value = ''
    isLoading.value = true
    loadingMessage.value = 'Подготавливаю просмотр...'
    resetViewportTransform()

    void resolveLoadingMessage(file)
      .then((message) => {
        if (selectionRequest === activeSelectionRequest && isLoading.value) {
          loadingMessage.value = message
        }
      })
      .catch(() => undefined)

    try {
      await ensureCapabilityMatrix()
      const resolvedEntry = await viewerRuntime.resolve(file, {
        onProgress(message) {
          if (selectionRequest === activeSelectionRequest) {
            loadingMessage.value = message
          }
        },
      })

      if (selectionRequest !== activeSelectionRequest) {
        releaseViewerEntry(resolvedEntry)
        return
      }

      selection.value = resolvedEntry
    } catch (error) {
      if (selectionRequest !== activeSelectionRequest) {
        return
      }

      errorMessage.value =
        error instanceof Error
          ? error.message
          : 'Не удалось подготовить просмотр для выбранного файла.'
    } finally {
      if (selectionRequest === activeSelectionRequest) {
        isLoading.value = false
      }
    }
  }

  function clearSelection() {
    activeSelectionRequest += 1
    releaseSelection()
    selection.value = null
    errorMessage.value = ''
    isLoading.value = false
    loadingMessage.value = 'Подготавливаю просмотр...'
    resetViewportTransform()
  }

  function zoomIn() {
    zoom.value = Number(Math.min(zoom.value + 0.2, 3).toFixed(2))
  }

  function zoomOut() {
    zoom.value = Number(Math.max(zoom.value - 0.2, 0.4).toFixed(2))
  }

  function rotateLeft() {
    rotation.value -= 90
  }

  function rotateRight() {
    rotation.value += 90
  }

  // Храним transform отдельно от preview-результата, чтобы все стратегии могли
  // пользоваться одной и той же логикой viewport без дублирования по компонентам.
  const viewportTransform = computed(
    () => `translate3d(0, 0, 0) scale(${zoom.value}) rotate(${rotation.value}deg)`,
  )

  onBeforeUnmount(() => {
    releaseSelection()
  })

  return {
    selection,
    isLoading,
    errorMessage,
    loadingMessage,
    viewerAcceptAttribute,
    imageFormats,
    documentFormats,
    mediaFormats,
    audioFormats,
    zoom,
    rotation,
    viewportTransform,
    selectFile,
    clearSelection,
    zoomIn,
    zoomOut,
    rotateLeft,
    rotateRight,
    resetViewportTransform,
  }
}

async function resolveLoadingMessage(file: File): Promise<string> {
  const format = await resolveViewerFormat(file.name, file.type)

  if (format?.previewStrategyId === 'server-viewer' && format.family === 'image') {
    return 'Подготавливаю изображение к просмотру. Для крупных HEIC, TIFF и RAW это может занять немного больше времени.'
  }

  if (format?.previewStrategyId === 'server-viewer' && format.family === 'document') {
    return 'Подготавливаю документ к просмотру и поиску. Для больших PDF, EPUB, офисных файлов и баз данных это может занять немного больше времени.'
  }

  if (format?.previewStrategyId === 'server-viewer' && format.family === 'media') {
    return 'Подготавливаю видео к воспроизведению. Для тяжёлых контейнеров и длинных роликов это может занять немного больше времени.'
  }

  if (format?.previewStrategyId === 'server-viewer' && format.family === 'audio') {
    return 'Подготавливаю аудио, волну сигнала и теги. Для длинных lossless-треков это может занять немного больше времени.'
  }

  if (format?.family === 'image') {
    return 'Подготавливаю изображение и метаданные...'
  }

  if (format?.family === 'media') {
    return 'Подготавливаю видео к просмотру...'
  }

  if (format?.family === 'audio') {
    return 'Подготавливаю аудио, волну и теги...'
  }

  return 'Подготавливаю просмотр...'
}
