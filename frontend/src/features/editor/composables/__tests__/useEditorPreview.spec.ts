import { effectScope, nextTick, ref } from 'vue'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { EditorLocalPreview } from '../../application/editor-preview'
import { useEditorPreview } from '../useEditorPreview'

function previewFor(label: string): EditorLocalPreview {
  return {
    mode: 'text',
    html: label,
    outline: [],
    note: null,
  }
}

describe('useEditorPreview', () => {
  afterEach(() => {
    vi.useRealTimers()
  })

  it('отменяет старую revision и не применяет поздний ответ', async () => {
    vi.useFakeTimers()
    const formatId = ref('markdown')
    const content = ref('first')
    const pending = new Map<string, (preview: EditorLocalPreview) => void>()
    const renderMarkdown = vi.fn(
      (source: string, _signal: AbortSignal) =>
        new Promise<EditorLocalPreview>((resolve) => pending.set(source, resolve)),
    )
    const scope = effectScope()
    const result = scope.run(() =>
      useEditorPreview(formatId, content, { debounceMs: 10, renderMarkdown }),
    )

    await vi.advanceTimersByTimeAsync(10)
    content.value = 'second'
    await nextTick()
    await vi.advanceTimersByTimeAsync(10)

    pending.get('second')?.(previewFor('second'))
    await Promise.resolve()
    pending.get('first')?.(previewFor('first'))
    await Promise.resolve()

    expect(result?.preview.value.html).toBe('second')
    scope.stop()
  })

  it('останавливает debounce и request при уничтожении scope', async () => {
    vi.useFakeTimers()
    const formatId = ref('markdown')
    const content = ref('draft')
    const signals: AbortSignal[] = []
    const renderMarkdown = vi.fn((_source: string, signal: AbortSignal) => {
      signals.push(signal)
      return new Promise<EditorLocalPreview>(() => undefined)
    })
    const scope = effectScope()

    scope.run(() => useEditorPreview(formatId, content, { debounceMs: 1, renderMarkdown }))
    await vi.advanceTimersByTimeAsync(1)
    scope.stop()

    expect(signals).toHaveLength(1)
    expect(signals[0]?.aborted).toBe(true)
  })
})
