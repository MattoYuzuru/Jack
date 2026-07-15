import { describe, expect, it } from 'vitest'
import {
  persistEditorDraft,
  readEditorDraft,
  setEditorPersistenceEnabled,
  type EditorDraftSnapshot,
} from '../editor-persistence'

const snapshot: EditorDraftSnapshot = {
  version: 2,
  formatId: 'markdown',
  fileName: 'notes.md',
  content: '# Notes',
  templateId: '',
  encoding: 'utf-8',
  newline: 'lf',
}

describe('editor persistence service', () => {
  it('is opt-in and versioned', () => {
    const storage = createMemoryStorage()
    expect(persistEditorDraft(storage, snapshot).status).toBe('disabled')
    expect(readEditorDraft(storage)).toBeNull()

    setEditorPersistenceEnabled(storage, true)
    expect(persistEditorDraft(storage, snapshot).status).toBe('saved')
    expect(readEditorDraft(storage)).toEqual(snapshot)
  })

  it('rejects oversized recovery snapshots', () => {
    const storage = createMemoryStorage()
    setEditorPersistenceEnabled(storage, true)

    const result = persistEditorDraft(storage, { ...snapshot, content: 'x'.repeat(600_000) })

    expect(result.status).toBe('too-large')
    expect(readEditorDraft(storage)).toBeNull()
  })

  it('clears secret-free legacy schema instead of restoring it', () => {
    const storage = createMemoryStorage()
    storage.setItem('jack.editor.persistence-enabled.v1', 'true')
    storage.setItem('jack.editor.draft.v1', JSON.stringify({ content: 'legacy' }))

    expect(readEditorDraft(storage)).toBeNull()
    expect(storage.getItem('jack.editor.draft.v1')).toBeNull()
  })
})

function createMemoryStorage(): Storage {
  const values = new Map<string, string>()
  return {
    get length() {
      return values.size
    },
    clear: () => values.clear(),
    getItem: (key) => values.get(key) ?? null,
    key: (index) => [...values.keys()][index] ?? null,
    removeItem: (key) => values.delete(key),
    setItem: (key, value) => values.set(key, value),
  }
}
