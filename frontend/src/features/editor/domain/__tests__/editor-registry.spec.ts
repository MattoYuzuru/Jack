import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createEditorCapabilityScopeFixture } from '../../../processing/application/__tests__/capability-matrix.fixtures'
import { resetProcessingCapabilityScopeCache } from '../../../processing/application/processing-client'
import {
  getEditorAcceptAttribute,
  resolveEditorFormat,
  resolveEditorFormatById,
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
})
