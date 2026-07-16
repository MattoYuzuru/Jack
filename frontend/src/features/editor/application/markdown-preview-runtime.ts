import { requestProcessingJson } from '../../processing/application/processing-client'
import type { EditorLocalPreview } from './editor-preview'

interface MarkdownOutlineItem {
  id: string
  label: string
  depth: number
  kind: string
}

export interface MarkdownRenderContract {
  profileVersion: string
  profile: 'commonmark-gfm' | 'obsidian-safe'
  sanitizedHtml: string
  previewDocument: string
  outline: MarkdownOutlineItem[]
  unresolvedReferences: Array<{ kind: string; target: string; label: string }>
  warnings: string[]
  detectedFeatures: string[]
}

export async function renderMarkdownPreview(
  source: string,
  signal?: AbortSignal,
): Promise<EditorLocalPreview> {
  const contract = await requestProcessingJson<MarkdownRenderContract>('/api/markdown/render', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      source,
      profile: 'obsidian-safe',
      extensions: [
        'footnotes',
        'definition-lists',
        'heading-anchors',
        'toc',
        'highlight',
        'sub-sup',
      ],
    }),
    signal,
  })

  const notes = [
    `Профиль ${contract.profileVersion}`,
    ...contract.warnings,
    contract.unresolvedReferences.length
      ? `Неразрешённых vault-ссылок: ${contract.unresolvedReferences.length}`
      : '',
  ].filter(Boolean)

  return {
    mode: 'sandbox',
    html: contract.previewDocument,
    outline: contract.outline,
    note: notes.join(' · '),
  }
}
