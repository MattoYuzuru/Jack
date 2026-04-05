import { computed, onBeforeUnmount, ref, shallowRef } from 'vue'
import { resolveViewerFormat } from '../domain/viewer-registry'
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
  const loadingMessage = ref('Подготавливаю preview...')
  const zoom = ref(1)
  const rotation = ref(0)

  function releaseSelection() {
    releaseViewerEntry(selection.value)
  }

  function resetViewportTransform() {
    zoom.value = 1
    rotation.value = 0
  }

  async function selectFile(file: File) {
    releaseSelection()
    selection.value = null
    errorMessage.value = ''
    isLoading.value = true
    loadingMessage.value = resolveLoadingMessage(file)
    resetViewportTransform()

    try {
      selection.value = await viewerRuntime.resolve(file)
    } catch (error) {
      errorMessage.value =
        error instanceof Error
          ? error.message
          : 'Не удалось подготовить preview для выбранного файла.'
    } finally {
      isLoading.value = false
    }
  }

  function clearSelection() {
    releaseSelection()
    selection.value = null
    errorMessage.value = ''
    isLoading.value = false
    loadingMessage.value = 'Подготавливаю preview...'
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

function resolveLoadingMessage(file: File): string {
  const format = resolveViewerFormat(file.name, file.type)

  if (format?.previewStrategyId === 'legacy-video') {
    return 'Подготавливаю video preview через legacy decode bridge. Для больших контейнеров это может занять больше времени, чем browser-native path.'
  }

  if (format?.previewStrategyId === 'legacy-audio') {
    return 'Подготавливаю audio preview через legacy transcode bridge. Для длинных lossless-треков это может занять больше времени, чем browser-native path.'
  }

  if (format?.family === 'document') {
    return 'Подготавливаю document preview и searchable text layer...'
  }

  if (format?.family === 'image') {
    return 'Подготавливаю image preview и metadata payload...'
  }

  if (format?.family === 'media') {
    return 'Подготавливаю video preview и playback metadata...'
  }

  if (format?.family === 'audio') {
    return 'Подготавливаю audio preview, waveform и tag metadata...'
  }

  return 'Подготавливаю preview...'
}
