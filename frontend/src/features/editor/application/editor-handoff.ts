export interface EditorIncomingDraft {
  formatId: string
  fileName: string
  content: string
  sourceLabel: string
}

export const MAX_EDITOR_HANDOFF_BYTES = 2 * 1024 * 1024

export type EditorHandoffResult = { accepted: true } | { accepted: false; message: string }

let incomingDraft: EditorIncomingDraft | null = null

export function stashEditorIncomingDraft(draft: EditorIncomingDraft): EditorHandoffResult {
  const sizeBytes = new TextEncoder().encode(draft.content).byteLength

  if (sizeBytes > MAX_EDITOR_HANDOFF_BYTES) {
    return {
      accepted: false,
      message:
        'Рабочая копия слишком большая для быстрой передачи в Editor. Скачай её или открой исходный файл в Editor напрямую.',
    }
  }

  // Handoff нужен только для следующего route-перехода. localStorage здесь был
  // лишним: крупный документ оставался на диске браузера и переживал сессию.
  incomingDraft = { ...draft }

  return { accepted: true }
}

export function consumeEditorIncomingDraft(): EditorIncomingDraft | null {
  const draft = incomingDraft
  incomingDraft = null
  return draft
}
