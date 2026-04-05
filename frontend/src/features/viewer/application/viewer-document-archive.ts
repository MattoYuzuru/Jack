import JSZip from 'jszip'
import type {
  ViewerDocumentOutlineItem,
  ViewerDocumentPreviewPayload,
} from './viewer-document'
import { escapeHtml, resolveOoxmlPath, wrapViewerDocumentHtml } from './viewer-ooxml'

interface ViewerNarrativeBlock {
  kind: 'heading' | 'paragraph' | 'table'
  level?: number
  text?: string
  rows?: string[][]
}

interface EpubManifestItem {
  id: string
  href: string
  mediaType: string
  properties: string[]
}

export async function buildOdtDocumentPreview(file: File): Promise<ViewerDocumentPreviewPayload> {
  const zip = await JSZip.loadAsync(await file.arrayBuffer())
  const content = await zip.file('content.xml')?.async('text')

  if (!content) {
    throw new Error('ODT adapter не нашёл content.xml внутри архива документа.')
  }

  const documentRoot = parseXml(content)
  const textRoot = findFirstElementByLocalName(documentRoot, 'text')

  if (!textRoot) {
    throw new Error('ODT adapter не нашёл office:text внутри content.xml.')
  }

  const blocks = parseOdtBlocks(textRoot)
  const searchableText = buildNarrativeText(blocks)
  const outline = blocks
    .filter((block): block is ViewerNarrativeBlock & { level: number; text: string } =>
      block.kind === 'heading' && typeof block.level === 'number' && typeof block.text === 'string',
    )
    .map((block, index) => ({
      id: `odt-heading-${index + 1}`,
      label: block.text,
      level: block.level,
    }))
  const tableCount = blocks.filter((block) => block.kind === 'table').length

  return {
    summary: [
      { label: 'Тип документа', value: 'ODT' },
      { label: 'Блоки', value: String(blocks.length) },
      { label: 'Headings', value: String(outline.length) },
      { label: 'Таблицы', value: String(tableCount) },
    ],
    searchableText,
    warnings: [
      'ODT preview собирается из archive/xml content layer: текст, headings и таблицы читаются, но стили, колонтитулы, изображения и footnotes сводятся к упрощённому document HTML.',
    ],
    layout: {
      mode: 'html',
      text: searchableText,
      srcDoc: wrapViewerDocumentHtml(renderNarrativeBlocks(blocks)),
      outline,
    },
    previewLabel: 'ODT archive adapter',
  }
}

export async function buildEpubDocumentPreview(file: File): Promise<ViewerDocumentPreviewPayload> {
  const zip = await JSZip.loadAsync(await file.arrayBuffer())
  const containerContent = await zip.file('META-INF/container.xml')?.async('text')

  if (!containerContent) {
    throw new Error('EPUB adapter не нашёл META-INF/container.xml внутри архива.')
  }

  const containerRoot = parseXml(containerContent)
  const rootFile = findFirstElementByLocalName(containerRoot, 'rootfile')
  const packagePath = rootFile?.getAttribute('full-path')?.trim()

  if (!packagePath) {
    throw new Error('EPUB adapter не смог определить OPF package path из container.xml.')
  }

  const packageContent = await zip.file(packagePath)?.async('text')
  if (!packageContent) {
    throw new Error(`EPUB adapter не нашёл package-документ по пути ${packagePath}.`)
  }

  const packageRoot = parseXml(packageContent)
  const manifest = readEpubManifest(packageRoot)
  const spine = findElementsByLocalName(packageRoot, 'itemref')
  const outline: ViewerDocumentOutlineItem[] = []
  const sectionHtml: string[] = []
  const sectionTexts: string[] = []
  let chapterCount = 0

  for (const [spineIndex, itemRef] of spine.entries()) {
    const idref = itemRef.getAttribute('idref')?.trim()
    const manifestItem = idref ? manifest.get(idref) : null

    if (!manifestItem || !isRenderableEpubItem(manifestItem.mediaType)) {
      continue
    }

    const chapterPath = resolveOoxmlPath(packagePath, manifestItem.href)
    const chapterContent = await zip.file(chapterPath)?.async('text')

    if (!chapterContent) {
      continue
    }

    const chapter = parseEpubChapter(chapterContent, spineIndex, manifestItem)
    if (!chapter.text) {
      continue
    }

    chapterCount += 1
    sectionTexts.push(chapter.text)
    sectionHtml.push(`<section class="epub-chapter">${chapter.html}</section>`)
    outline.push(...chapter.outline)
  }

  const title = readMetadataValue(packageRoot, 'title') || file.name
  const author = readMetadataValue(packageRoot, 'creator') || 'Не определён'
  const language = readMetadataValue(packageRoot, 'language') || 'Не определён'
  const searchableText = sectionTexts.join('\n\n')

  if (!chapterCount) {
    throw new Error('EPUB adapter не нашёл ни одной главы с читаемым текстовым содержимым.')
  }

  return {
    summary: [
      { label: 'Тип документа', value: 'EPUB' },
      { label: 'Название', value: title },
      { label: 'Автор', value: author },
      { label: 'Язык', value: language },
      { label: 'Главы', value: String(chapterCount) },
    ],
    searchableText,
    warnings: [
      'EPUB preview рендерится как reflowable reading layer: viewer показывает главы и headings, но не воспроизводит исходные CSS-темы, встроенные шрифты, annotations и media overlays.',
    ],
    layout: {
      mode: 'html',
      text: searchableText,
      srcDoc: wrapViewerDocumentHtml(sectionHtml.join('')),
      outline,
    },
    previewLabel: 'EPUB reading adapter',
  }
}

function parseOdtBlocks(textRoot: Element): ViewerNarrativeBlock[] {
  const blocks: ViewerNarrativeBlock[] = []

  for (const child of Array.from(textRoot.children)) {
    collectOdtBlocks(child, blocks)
  }

  return blocks
}

function collectOdtBlocks(node: Element, blocks: ViewerNarrativeBlock[], bulletPrefix = '') {
  switch (node.localName) {
    case 'h': {
      const text = normalizeInlineText(readOdtInlineText(node))
      if (!text) {
        return
      }

      const level = Number(node.getAttribute('text:outline-level') ?? node.getAttribute('outline-level') ?? '1')

      blocks.push({
        kind: 'heading',
        level: Number.isFinite(level) ? Math.min(Math.max(level, 1), 6) : 1,
        text,
      })
      return
    }
    case 'p': {
      const text = normalizeInlineText(readOdtInlineText(node))
      if (!text) {
        return
      }

      blocks.push({
        kind: 'paragraph',
        text: bulletPrefix ? `${bulletPrefix}${text}` : text,
      })
      return
    }
    case 'list':
      for (const child of Array.from(node.children)) {
        collectOdtBlocks(child, blocks, bulletPrefix || '• ')
      }
      return
    case 'list-item':
    case 'section':
    case 'span':
    case 'body':
      for (const child of Array.from(node.children)) {
        collectOdtBlocks(child, blocks, bulletPrefix)
      }
      return
    case 'table': {
      const rows = parseOdtTable(node)
      if (rows.length) {
        blocks.push({
          kind: 'table',
          rows,
        })
      }
      return
    }
    case 'sequence-decls':
    case 'tracked-changes':
    case 'variable-decls':
      return
    default:
      for (const child of Array.from(node.children)) {
        collectOdtBlocks(child, blocks, bulletPrefix)
      }
  }
}

function parseOdtTable(tableNode: Element): string[][] {
  const rows: string[][] = []

  for (const rowNode of Array.from(tableNode.children).filter((child) => child.localName === 'table-row')) {
    const cells = Array.from(rowNode.children)
      .filter((child) => child.localName === 'table-cell')
      .map((cellNode) =>
        normalizeInlineText(
          Array.from(cellNode.children)
            .filter((child) => child.localName === 'p' || child.localName === 'h')
            .map((paragraph) => readOdtInlineText(paragraph))
            .join(' '),
        ),
      )
      .filter((value, index, values) => value.length > 0 || index < values.length - 1)

    if (cells.length) {
      rows.push(cells)
    }
  }

  return rows
}

function readOdtInlineText(node: Node): string {
  if (node.nodeType === Node.TEXT_NODE) {
    return node.textContent ?? ''
  }

  if (!(node instanceof Element)) {
    return ''
  }

  if (node.localName === 's') {
    return ' '.repeat(Number(node.getAttribute('text:c') ?? node.getAttribute('c') ?? '1'))
  }

  if (node.localName === 'tab') {
    return '\t'
  }

  if (node.localName === 'line-break') {
    return '\n'
  }

  if (node.localName === 'note' || node.localName === 'frame') {
    return ''
  }

  return Array.from(node.childNodes)
    .map((childNode) => readOdtInlineText(childNode))
    .join('')
}

function readEpubManifest(packageRoot: XMLDocument): Map<string, EpubManifestItem> {
  const items = new Map<string, EpubManifestItem>()

  for (const itemNode of findElementsByLocalName(packageRoot, 'item')) {
    const id = itemNode.getAttribute('id')?.trim()
    const href = itemNode.getAttribute('href')?.trim()
    const mediaType = itemNode.getAttribute('media-type')?.trim() ?? ''

    if (!id || !href) {
      continue
    }

    items.set(id, {
      id,
      href,
      mediaType,
      properties: (itemNode.getAttribute('properties') ?? '')
        .split(/\s+/u)
        .map((value) => value.trim())
        .filter(Boolean),
    })
  }

  return items
}

function isRenderableEpubItem(mediaType: string): boolean {
  return (
    mediaType === 'application/xhtml+xml' ||
    mediaType === 'text/html' ||
    mediaType === 'application/x-dtbook+xml'
  )
}

function parseEpubChapter(
  content: string,
  chapterIndex: number,
  manifestItem: EpubManifestItem,
): {
  html: string
  text: string
  outline: ViewerDocumentOutlineItem[]
} {
  const documentRoot = new DOMParser().parseFromString(content, 'text/html')
  documentRoot.querySelectorAll('script, style, iframe, object, embed').forEach((node) => node.remove())

  const blocks = collectHtmlNarrativeBlocks(documentRoot.body)
  const text = buildNarrativeText(blocks)
  const chapterLabel = blocks.find((block) => block.kind === 'heading')?.text ?? manifestItem.id
  const outline = blocks
    .filter((block): block is ViewerNarrativeBlock & { level: number; text: string } =>
      block.kind === 'heading' && typeof block.level === 'number' && typeof block.text === 'string',
    )
    .map((block, headingIndex) => ({
      id: `epub-${chapterIndex + 1}-${headingIndex + 1}`,
      label: block.text,
      level: block.level,
    }))

  const htmlBody = renderNarrativeBlocks(blocks)
  const headingHtml =
    blocks[0]?.kind === 'heading'
      ? ''
      : `<h1>${escapeHtml(chapterLabel || `Chapter ${chapterIndex + 1}`)}</h1>`

  return {
    html: `${headingHtml}${htmlBody}`,
    text,
    outline,
  }
}

function collectHtmlNarrativeBlocks(node: ParentNode, bulletPrefix = ''): ViewerNarrativeBlock[] {
  const blocks: ViewerNarrativeBlock[] = []

  for (const child of Array.from(node.childNodes)) {
    if (child.nodeType === Node.TEXT_NODE) {
      continue
    }

    if (!(child instanceof HTMLElement)) {
      continue
    }

    const tag = child.tagName.toUpperCase()

    if (/^H[1-6]$/u.test(tag)) {
      const text = normalizeInlineText(child.textContent ?? '')
      if (text) {
        blocks.push({
          kind: 'heading',
          level: Number(tag.slice(1)),
          text,
        })
      }
      continue
    }

    if (tag === 'P' || tag === 'BLOCKQUOTE' || tag === 'PRE') {
      const text = normalizeInlineText(child.textContent ?? '')
      if (text) {
        blocks.push({
          kind: 'paragraph',
          text: bulletPrefix ? `${bulletPrefix}${text}` : text,
        })
      }
      continue
    }

    if (tag === 'TABLE') {
      const rows = Array.from(child.querySelectorAll('tr'))
        .map((rowNode) =>
          Array.from(rowNode.querySelectorAll('th,td'))
            .map((cellNode) => normalizeInlineText(cellNode.textContent ?? ''))
            .filter((value, index, values) => value.length > 0 || index < values.length - 1),
        )
        .filter((row) => row.length > 0)

      if (rows.length) {
        blocks.push({
          kind: 'table',
          rows,
        })
      }
      continue
    }

    if (tag === 'UL' || tag === 'OL') {
      blocks.push(...collectHtmlNarrativeBlocks(child, '• '))
      continue
    }

    if (tag === 'LI') {
      const text = normalizeInlineText(child.textContent ?? '')
      if (text) {
        blocks.push({
          kind: 'paragraph',
          text: `${bulletPrefix || '• '}${text}`,
        })
      }
      continue
    }

    blocks.push(...collectHtmlNarrativeBlocks(child, bulletPrefix))
  }

  return blocks
}

function renderNarrativeBlocks(blocks: ViewerNarrativeBlock[]): string {
  return blocks
    .map((block) => {
      if (block.kind === 'heading') {
        return `<h${block.level}>${escapeHtml(block.text ?? '')}</h${block.level}>`
      }

      if (block.kind === 'table') {
        return renderNarrativeTable(block.rows ?? [])
      }

      const text = block.text ?? ''

      if (text.startsWith('• ')) {
        return `<ul class="docx-list"><li>${escapeHtml(text.slice(2))}</li></ul>`
      }

      return `<p>${escapeHtml(text)}</p>`
    })
    .join('')
}

function renderNarrativeTable(rows: string[][]): string {
  if (!rows.length) {
    return ''
  }

  const [headerRow = [], ...bodyRows] = rows
  const headerHtml = `<tr>${headerRow.map((cell) => `<th>${escapeHtml(cell)}</th>`).join('')}</tr>`
  const bodyHtml = bodyRows
    .map((row) => `<tr>${row.map((cell) => `<td>${escapeHtml(cell)}</td>`).join('')}</tr>`)
    .join('')

  return `<table><thead>${headerHtml}</thead><tbody>${bodyHtml}</tbody></table>`
}

function buildNarrativeText(blocks: ViewerNarrativeBlock[]): string {
  return blocks
    .map((block) => {
      if (block.kind === 'table') {
        return (block.rows ?? []).map((row) => row.join(' ')).join('\n')
      }

      return block.text ?? ''
    })
    .filter(Boolean)
    .join('\n\n')
}

function normalizeInlineText(value: string): string {
  return value.replace(/\s+/gu, ' ').trim()
}

function readMetadataValue(packageRoot: XMLDocument, localName: string): string {
  return (
    findElementsByLocalName(packageRoot, localName)
      .map((node) => node.textContent?.trim() ?? '')
      .find(Boolean) ?? ''
  )
}

function parseXml(content: string): XMLDocument {
  return new DOMParser().parseFromString(content, 'application/xml')
}

function findFirstElementByLocalName(node: XMLDocument | Element, localName: string): Element | null {
  return findElementsByLocalName(node, localName)[0] ?? null
}

function findElementsByLocalName(node: XMLDocument | Element, localName: string): Element[] {
  return Array.from(node.getElementsByTagNameNS('*', localName))
}
