export interface EditorDraftSnapshot {
  version: 2
  formatId: string
  fileName: string
  content: string
  templateId: string
  encoding: 'utf-8' | 'utf-8-bom'
  newline: 'lf' | 'crlf'
}

export type EditorPersistenceResult =
  | { status: 'saved'; sizeBytes: number }
  | { status: 'disabled' | 'too-large' | 'quota-error'; sizeBytes: number }

const STORAGE_KEY = 'jack.editor.draft.v2'
const LEGACY_STORAGE_KEY = 'jack.editor.draft.v1'
const ENABLED_KEY = 'jack.editor.persistence-enabled.v1'
const MAX_PERSISTED_BYTES = 512 * 1024

export function isEditorPersistenceEnabled(storage: Storage): boolean {
  return storage.getItem(ENABLED_KEY) === 'true'
}

export function setEditorPersistenceEnabled(storage: Storage, enabled: boolean): void {
  storage.setItem(ENABLED_KEY, String(enabled))
  storage.removeItem(LEGACY_STORAGE_KEY)
  if (!enabled) {
    storage.removeItem(STORAGE_KEY)
  }
}

export function readEditorDraft(storage: Storage): EditorDraftSnapshot | null {
  storage.removeItem(LEGACY_STORAGE_KEY)
  if (!isEditorPersistenceEnabled(storage)) {
    storage.removeItem(STORAGE_KEY)
    return null
  }

  try {
    const rawValue = storage.getItem(STORAGE_KEY)
    if (!rawValue) {
      return null
    }
    const value = JSON.parse(rawValue) as Partial<EditorDraftSnapshot>
    if (
      value.version !== 2 ||
      typeof value.formatId !== 'string' ||
      typeof value.fileName !== 'string' ||
      typeof value.content !== 'string' ||
      typeof value.templateId !== 'string' ||
      !['utf-8', 'utf-8-bom'].includes(value.encoding ?? '') ||
      !['lf', 'crlf'].includes(value.newline ?? '')
    ) {
      storage.removeItem(STORAGE_KEY)
      return null
    }

    return value as EditorDraftSnapshot
  } catch {
    storage.removeItem(STORAGE_KEY)
    return null
  }
}

export function persistEditorDraft(
  storage: Storage,
  snapshot: EditorDraftSnapshot,
): EditorPersistenceResult {
  if (!isEditorPersistenceEnabled(storage)) {
    storage.removeItem(STORAGE_KEY)
    return { status: 'disabled', sizeBytes: 0 }
  }

  const serialized = JSON.stringify(snapshot)
  const sizeBytes = new TextEncoder().encode(serialized).byteLength
  if (sizeBytes > MAX_PERSISTED_BYTES) {
    storage.removeItem(STORAGE_KEY)
    return { status: 'too-large', sizeBytes }
  }

  try {
    storage.setItem(STORAGE_KEY, serialized)
    return { status: 'saved', sizeBytes }
  } catch {
    return { status: 'quota-error', sizeBytes }
  }
}

export function clearEditorDraft(storage: Storage): void {
  storage.removeItem(STORAGE_KEY)
  storage.removeItem(LEGACY_STORAGE_KEY)
}
