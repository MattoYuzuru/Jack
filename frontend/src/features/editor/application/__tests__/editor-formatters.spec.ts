import { describe, expect, it } from 'vitest'
import { canFormatEditorFormat, formatEditorContent } from '../editor-formatters'

describe('editor formatters', () => {
  it('formats json and javascript drafts with prettier-powered browser formatting', async () => {
    const formattedJson = await formatEditorContent('json', '{"alpha":1,"beta":[2,3]}')
    const formattedJs = await formatEditorContent(
      'javascript',
      "export async function load(){return fetch('/api')}",
    )

    expect(formattedJson).toContain('"alpha": 1')
    expect(formattedJson).toContain('"beta": [')
    expect(formattedJs).toContain('export async function load()')
    expect(formattedJs).toContain("return fetch('/api')")
  })

  it('reports formatting support by format id', () => {
    expect(canFormatEditorFormat('markdown')).toBe(true)
    expect(canFormatEditorFormat('txt')).toBe(false)
  })
})
