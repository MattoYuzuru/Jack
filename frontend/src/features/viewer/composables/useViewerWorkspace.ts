import { computed, onBeforeUnmount, ref, shallowRef } from 'vue'
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
