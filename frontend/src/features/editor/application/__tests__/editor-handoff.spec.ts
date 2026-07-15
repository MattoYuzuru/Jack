import { afterEach, describe, expect, it } from 'vitest'
import {
  consumeEditorIncomingDraft,
  MAX_EDITOR_HANDOFF_BYTES,
  stashEditorIncomingDraft,
} from '../editor-handoff'

describe('editor handoff', () => {
  afterEach(() => {
    consumeEditorIncomingDraft()
  })

  it('keeps a route handoff only in memory and consumes it once', () => {
    const result = stashEditorIncomingDraft({
      formatId: 'markdown',
      fileName: 'notes.md',
      content: '# Notes',
      sourceLabel: 'viewer.pdf',
    })

    expect(result).toEqual({ accepted: true })
    expect(consumeEditorIncomingDraft()).toEqual({
      formatId: 'markdown',
      fileName: 'notes.md',
      content: '# Notes',
      sourceLabel: 'viewer.pdf',
    })
    expect(consumeEditorIncomingDraft()).toBeNull()
  })

  it('rejects an oversized handoff instead of retaining a large document in browser storage', () => {
    const result = stashEditorIncomingDraft({
      formatId: 'txt',
      fileName: 'large.txt',
      content: 'x'.repeat(MAX_EDITOR_HANDOFF_BYTES + 1),
      sourceLabel: 'viewer.txt',
    })

    expect(result).toMatchObject({ accepted: false })
    expect(consumeEditorIncomingDraft()).toBeNull()
  })
})
