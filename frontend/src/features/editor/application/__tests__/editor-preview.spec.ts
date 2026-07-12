import { describe, expect, it } from 'vitest'
import { buildEditorLocalPreview } from '../editor-preview'

const MARKDOWN_XSS_PAYLOADS = [
  '[bad](javascript:document.body.dataset.pwned=1)',
  '[mixed](JaVaScRiPt:alert(1))',
  '[space]( java\nscript:alert(1))',
  '[quote](https://example.com/" onmouseover="alert(1))',
  '<img src=x onerror=alert(1)>',
  '<svg><a href="data:text/html,<script>alert(1)</script>">bad</a></svg>',
  '[nested [label]](javascript:alert(1))',
  '[broken](javascript:alert(1)',
]

describe('editor markdown preview security', () => {
  it.each(MARKDOWN_XSS_PAYLOADS)('renders untrusted Markdown as inert text: %s', (payload) => {
    const preview = buildEditorLocalPreview('markdown', payload)

    expect(preview.mode).toBe('text')
    expect(preview.html).not.toMatch(/<(?:a|img|svg|script)\b/iu)
  })

  it('keeps outline extraction while rich rendering is disabled', () => {
    const preview = buildEditorLocalPreview('markdown', '# Безопасный заголовок\n\nТекст')

    expect(preview.outline).toEqual([
      expect.objectContaining({ label: 'Безопасный заголовок', depth: 1 }),
    ])
  })
})
