import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createEditorCapabilityScopeFixture } from '../../../processing/application/__tests__/capability-matrix.fixtures'
import { resetProcessingCapabilityScopeCache } from '../../../processing/application/processing-client'
import {
  getEditorAcceptAttribute,
  resolveEditorFormat,
  resolveEditorFormatMatch,
  resolveEditorFormatById,
  type EditorCapabilityMatrix,
} from '../editor-registry'

const originalFetch = globalThis.fetch

describe('editor registry', () => {
  beforeEach(() => {
    resetProcessingCapabilityScopeCache()
    globalThis.fetch = vi.fn().mockResolvedValue(
      new Response(JSON.stringify(createEditorCapabilityScopeFixture()), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    ) as typeof fetch
  })

  afterEach(() => {
    resetProcessingCapabilityScopeCache()
    globalThis.fetch = originalFetch
  })

  it('resolves supported text-centric formats by file name', async () => {
    expect((await resolveEditorFormat('README.markdown'))?.id).toBe('markdown')
    expect((await resolveEditorFormat('layout.html'))?.previewMode).toBe('sandbox')
    expect((await resolveEditorFormat('theme.css'))?.syntaxMode).toBe('css')
    expect((await resolveEditorFormat('payload.json'))?.id).toBe('json')
  })

  it('exposes editor accept attribute and direct id lookup', async () => {
    expect(await getEditorAcceptAttribute()).toContain('.md')
    expect((await resolveEditorFormatById('yaml'))?.label).toBe('YAML')
    expect((await resolveEditorFormatById('txt'))?.supportsFormatting).toBe(false)
  })

  it('prefers extension and reports a recognized MIME mismatch', async () => {
    const fixture = createEditorCapabilityScopeFixture()
    const formats = (fixture.editorMatrix as EditorCapabilityMatrix | null)?.formats ?? []
    const markdown = formats.find((format) => format.id === 'markdown')
    const plainText = formats.find((format) => format.id === 'txt')
    if (markdown && plainText) {
      markdown.mimeTypes = ['text/markdown']
      plainText.mimeTypes = ['text/plain']
    }
    globalThis.fetch = vi.fn().mockResolvedValue(
      new Response(JSON.stringify(fixture), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    ) as typeof fetch
    resetProcessingCapabilityScopeCache()

    const resolution = await resolveEditorFormatMatch('notes.md', 'text/plain')

    expect(resolution.format?.id).toBe('markdown')
    expect(resolution.source).toBe('extension')
    expect(resolution.mismatchWarning).toContain('text/plain')
  })
})
