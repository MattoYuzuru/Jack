import { describe, expect, it } from 'vitest'
import { buildViewerFacts, formatViewerPreviewLabel } from '../viewer-presentation'
import type { ViewerResolvedEntry } from '../viewer-runtime'

const markdownFormat = {
  extension: 'md',
  aliases: ['markdown'],
  label: 'Markdown',
  family: 'document' as const,
  mimeTypes: ['text/markdown'],
  previewPipeline: 'server-assisted' as const,
  previewStrategyId: 'server-viewer' as const,
  statusLabel: 'Ready',
  notes: '',
  accents: [],
  available: true,
  availabilityDetail: null,
  requiredJobTypes: ['VIEWER_RESOLVE'],
}

describe('viewer presentation', () => {
  it('labels a Markdown document consistently with the renderer mode', () => {
    const selection = {
      kind: 'document',
      file: new File(['# Jack'], 'notes.md', { type: 'text/markdown' }),
      extension: 'md',
      format: markdownFormat,
      summary: [{ label: 'Headings', value: '1' }],
      searchableText: 'Jack',
      warnings: [],
      layout: {
        mode: 'html' as const,
        text: '# Jack',
        srcDoc: '<h1>Jack</h1>',
        outline: [{ id: 'jack', label: 'Jack', level: 1 }],
        editableDraft: null,
      },
      previewLabel: 'Markdown reading preview',
    } satisfies ViewerResolvedEntry

    expect(formatViewerPreviewLabel(selection)).toBe('Веб-предпросмотр')
    expect(buildViewerFacts(selection)).toEqual(
      expect.arrayContaining([
        { label: 'Режим просмотра', value: 'Веб-предпросмотр' },
        { label: 'Заголовки', value: '1' },
      ]),
    )
  })

  it('returns no facts for an empty selection', () => {
    expect(buildViewerFacts(null)).toEqual([])
    expect(formatViewerPreviewLabel(null)).toBe('')
  })
})
