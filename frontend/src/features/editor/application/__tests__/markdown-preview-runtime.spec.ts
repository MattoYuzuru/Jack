import { afterEach, describe, expect, it, vi } from 'vitest'
import { renderMarkdownPreview } from '../markdown-preview-runtime'

describe('markdown preview runtime', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('renders only the sanitized backend contract in an isolated document', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(
        async () =>
          new Response(
            JSON.stringify({
              profileVersion: 'jack-markdown-1.0.0',
              profile: 'obsidian-safe',
              sanitizedHtml: '<h1 id="safe">Safe</h1><p>Body</p>',
              outline: [{ id: 'safe', label: 'Safe', depth: 1, kind: 'heading' }],
              unresolvedReferences: [],
              warnings: [],
              detectedFeatures: [],
            }),
            { status: 200, headers: { 'Content-Type': 'application/json' } },
          ),
      ),
    )

    const preview = await renderMarkdownPreview('# ignored local source')

    expect(preview.mode).toBe('sandbox')
    expect(preview.html).toContain("default-src 'none'")
    expect(preview.html).toContain('<h1 id="safe">Safe</h1>')
    expect(preview.outline).toEqual([{ id: 'safe', label: 'Safe', depth: 1, kind: 'heading' }])
  })
})
