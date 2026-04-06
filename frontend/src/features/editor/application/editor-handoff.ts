export interface EditorIncomingDraft {
  formatId: string
  fileName: string
  content: string
  sourceLabel: string
}

const EDITOR_HANDOFF_STORAGE_KEY = 'jack.editor.incoming-draft.v1'

export function stashEditorIncomingDraft(draft: EditorIncomingDraft): void {
  if (typeof window === 'undefined') {
    return
  }

  window.localStorage.setItem(EDITOR_HANDOFF_STORAGE_KEY, JSON.stringify(draft))
}

export function consumeEditorIncomingDraft(): EditorIncomingDraft | null {
  if (typeof window === 'undefined') {
    return null
  }

  try {
    const rawValue = window.localStorage.getItem(EDITOR_HANDOFF_STORAGE_KEY)
    if (!rawValue) {
      return null
    }

    window.localStorage.removeItem(EDITOR_HANDOFF_STORAGE_KEY)
    const payload = JSON.parse(rawValue) as Partial<EditorIncomingDraft>

    if (
      typeof payload.formatId !== 'string' ||
      typeof payload.fileName !== 'string' ||
      typeof payload.content !== 'string' ||
      typeof payload.sourceLabel !== 'string'
    ) {
      return null
    }

    return {
      formatId: payload.formatId,
      fileName: payload.fileName,
      content: payload.content,
      sourceLabel: payload.sourceLabel,
    }
  } catch {
    return null
  }
}
