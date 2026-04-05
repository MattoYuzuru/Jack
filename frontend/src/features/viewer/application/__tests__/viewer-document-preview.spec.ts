import { describe, expect, it } from 'vitest'
import { findViewerDocumentMatches } from '../viewer-document'
import {
  decodeRtfDocument,
  parseDelimitedTextDocument,
  sanitizeHtmlDocument,
} from '../viewer-document-preview'

describe('viewer document preview', () => {
  it('parses csv content into a bounded table preview', () => {
    const table = parseDelimitedTextDocument(
      'Name,Role,Team\nAlice,Engineer,Viewer\nBob,Designer,Viewer\nCara,QA,Viewer',
    )

    expect(table.columns).toEqual(['Name', 'Role', 'Team'])
    expect(table.rows).toEqual([
      ['Alice', 'Engineer', 'Viewer'],
      ['Bob', 'Designer', 'Viewer'],
      ['Cara', 'QA', 'Viewer'],
    ])
    expect(table.totalRows).toBe(3)
    expect(table.totalColumns).toBe(3)
  })

  it('extracts readable text from simple rtf content', () => {
    const text = decodeRtfDocument('{\\rtf1\\ansi Hello\\par Viewer\\tab Workspace}')

    expect(text).toBe('Hello\nViewer\tWorkspace')
  })

  it('sanitizes html previews and keeps outline/text content', () => {
    const preview = sanitizeHtmlDocument(
      '<html><body><h1>Viewer</h1><p onclick="alert(1)">Docs</p><script>alert(1)</script></body></html>',
    )

    expect(preview.outline).toEqual([{ id: 'heading-1', label: 'Viewer', level: 1 }])
    expect(preview.textContent).toBe('Viewer Docs')
    expect(preview.srcDoc).not.toContain('<script>')
    expect(preview.srcDoc).not.toContain('onclick=')
    expect(preview.warnings).toContain('Из HTML preview удалены активные или потенциально опасные узлы.')
  })

  it('builds capped search excerpts for normalized document text', () => {
    const matches = findViewerDocumentMatches(
      'Viewer foundation adds pdf preview, text search and document outline for the workspace.',
      'document',
    )

    expect(matches).toHaveLength(1)
    expect(matches[0]?.excerpt).toContain('document outline')
  })
})
