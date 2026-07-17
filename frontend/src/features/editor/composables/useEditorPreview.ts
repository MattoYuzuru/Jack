import { computed, onScopeDispose, shallowRef, watch, type Ref } from 'vue'
import { buildEditorLocalPreview, type EditorLocalPreview } from '../application/editor-preview'
import { renderMarkdownPreview } from '../application/markdown-preview-runtime'

interface EditorPreviewOptions {
  debounceMs?: number
  renderMarkdown?: (source: string, signal: AbortSignal) => Promise<EditorLocalPreview>
}

export function useEditorPreview(
  formatId: Ref<string>,
  content: Ref<string>,
  options: EditorPreviewOptions = {},
) {
  const serverMarkdownPreview = shallowRef<EditorLocalPreview | null>(null)
  const debounceMs = options.debounceMs ?? 280
  const renderMarkdown = options.renderMarkdown ?? renderMarkdownPreview
  let timeoutId: ReturnType<typeof setTimeout> | null = null
  let revision = 0
  let controller: AbortController | null = null

  const preview = computed<EditorLocalPreview>(() => {
    if (formatId.value === 'markdown' && serverMarkdownPreview.value) {
      return serverMarkdownPreview.value
    }

    return buildEditorLocalPreview(formatId.value, content.value)
  })

  function cancelPendingPreview(): void {
    if (timeoutId !== null) {
      clearTimeout(timeoutId)
      timeoutId = null
    }
    controller?.abort()
    controller = null
  }

  watch(
    [formatId, content],
    ([nextFormatId, source]) => {
      cancelPendingPreview()
      const currentRevision = ++revision

      if (nextFormatId !== 'markdown') {
        serverMarkdownPreview.value = null
        return
      }

      timeoutId = setTimeout(() => {
        timeoutId = null
        const requestController = new AbortController()
        controller = requestController

        void renderMarkdown(source, requestController.signal)
          .then((nextPreview) => {
            if (currentRevision === revision && !requestController.signal.aborted) {
              serverMarkdownPreview.value = nextPreview
            }
          })
          .catch(() => {
            // Offline и штатная отмена оставляют безопасный inert preview текущей revision.
          })
          .finally(() => {
            if (currentRevision === revision) {
              controller = null
            }
          })
      }, debounceMs)
    },
    { immediate: true },
  )

  onScopeDispose(() => {
    revision += 1
    cancelPendingPreview()
  })

  return {
    preview,
    cancelPendingPreview,
  }
}
