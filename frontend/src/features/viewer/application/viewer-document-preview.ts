import pdfWorkerUrl from 'pdfjs-dist/build/pdf.worker.mjs?url'
import type {
  ViewerDocumentFact,
  ViewerDocumentOutlineItem,
  ViewerDocumentPreviewPayload,
  ViewerDocumentTablePreview,
} from './viewer-document'

interface PdfJsTextItem {
  str?: string
}

interface PdfJsModule {
  getDocument(input: { data: Uint8Array }): {
    promise: Promise<{
      numPages: number
      getPage(pageNumber: number): Promise<{
        getTextContent(): Promise<{ items: PdfJsTextItem[] }>
        cleanup(): void
      }>
      cleanup(): void
      destroy(): Promise<void>
    }>
    destroy(): Promise<void>
  }
  GlobalWorkerOptions: {
    workerSrc: string
  }
}

const pdfPageSearchLimit = 12
const csvPreviewRowLimit = 24

let pdfJsPromise: Promise<PdfJsModule> | null = null

export async function buildPdfDocumentPreview(file: File): Promise<ViewerDocumentPreviewPayload> {
  const bytes = new Uint8Array(await file.arrayBuffer())
  const objectUrl = URL.createObjectURL(file)
  const warnings: string[] = []
  let pageCount: number | null = null
  let searchableText = ''

  try {
    const pdfjs = await loadPdfJs()
    const loadingTask = pdfjs.getDocument({ data: bytes })
    const pdfDocument = await loadingTask.promise

    try {
      pageCount = pdfDocument.numPages
      const extractedText = await extractPdfSearchableText(pdfDocument)
      searchableText = extractedText.text
      warnings.push(...extractedText.warnings)
    } finally {
      pdfDocument.cleanup()
      await pdfDocument.destroy()
      await loadingTask.destroy()
    }
  } catch {
    warnings.push('PDF preview открыт через browser embed, но текстовый слой и page stats не удалось поднять.')
  }

  const summary: ViewerDocumentFact[] = [
    {
      label: 'Тип документа',
      value: 'PDF',
    },
    {
      label: 'Страниц',
      value: pageCount ? String(pageCount) : 'Не определено',
    },
    {
      label: 'Search layer',
      value: searchableText.trim() ? 'PDF text extraction' : 'Browser preview only',
    },
  ]

  return {
    summary,
    searchableText,
    warnings,
    layout: {
      mode: 'pdf',
      objectUrl,
      pageCount,
    },
    previewLabel: 'PDF browser preview',
  }
}

export async function buildTextDocumentPreview(file: File): Promise<ViewerDocumentPreviewPayload> {
  const text = readDocumentText(await file.arrayBuffer())
  const summary = buildTextSummary('TXT', text)

  return {
    summary,
    searchableText: text,
    warnings: [],
    layout: {
      mode: 'text',
      text,
      paragraphs: splitParagraphs(text),
    },
    previewLabel: 'Text decode adapter',
  }
}

export async function buildCsvDocumentPreview(file: File): Promise<ViewerDocumentPreviewPayload> {
  const text = readDocumentText(await file.arrayBuffer())
  const table = parseDelimitedTextDocument(text)
  const warnings: string[] = []

  if (table.totalRows > table.rows.length) {
    warnings.push(
      `CSV preview ограничен первыми ${csvPreviewRowLimit} строками, полная таблица остаётся в search layer.`,
    )
  }

  return {
    summary: [
      { label: 'Тип документа', value: 'CSV' },
      { label: 'Колонки', value: String(table.totalColumns) },
      { label: 'Строки', value: String(table.totalRows) },
      {
        label: 'Delimiter',
        value: describeDelimiter(table.delimiter),
      },
    ],
    searchableText: text,
    warnings,
    layout: {
      mode: 'table',
      text,
      table,
    },
    previewLabel: 'Delimited table preview',
  }
}

export async function buildHtmlDocumentPreview(file: File): Promise<ViewerDocumentPreviewPayload> {
  const text = readDocumentText(await file.arrayBuffer())
  const preview = sanitizeHtmlDocument(text)

  return {
    summary: [
      { label: 'Тип документа', value: 'HTML' },
      { label: 'Headings', value: String(preview.outline.length) },
      { label: 'Текстовых символов', value: String(preview.textContent.length) },
      { label: 'Sandbox', value: 'Iframe srcdoc' },
    ],
    searchableText: preview.textContent,
    warnings: preview.warnings,
    layout: {
      mode: 'html',
      text: preview.textContent,
      srcDoc: preview.srcDoc,
      outline: preview.outline,
    },
    previewLabel: 'HTML sandbox preview',
  }
}

export async function buildRtfDocumentPreview(file: File): Promise<ViewerDocumentPreviewPayload> {
  const source = readDocumentText(await file.arrayBuffer())
  const text = decodeRtfDocument(source)

  return {
    summary: buildTextSummary('RTF', text),
    searchableText: text,
    warnings: [
      'RTF сейчас проходит через text extraction path: форматирование и вложенные объекты не рендерятся как layout.',
    ],
    layout: {
      mode: 'text',
      text,
      paragraphs: splitParagraphs(text),
    },
    previewLabel: 'RTF text extraction',
  }
}

export function parseDelimitedTextDocument(content: string): ViewerDocumentTablePreview {
  const delimiter = detectDelimiter(content)
  const parsedRows = parseDelimitedRows(content, delimiter).filter((row) =>
    row.some((cell) => cell.trim().length > 0),
  )

  if (!parsedRows.length) {
    return {
      columns: [],
      rows: [],
      totalRows: 0,
      totalColumns: 0,
      delimiter,
    }
  }

  const maxColumns = Math.max(...parsedRows.map((row) => row.length), 0)
  const headerRow = parsedRows[0] ?? []
  const hasDistinctHeader = headerRow.some((cell) => cell.trim().length > 0) && parsedRows.length > 1
  const columns = hasDistinctHeader
    ? padRow(headerRow, maxColumns).map((cell, index) => cell.trim() || `Column ${index + 1}`)
    : Array.from({ length: maxColumns }, (_, index) => `Column ${index + 1}`)
  const dataRows = hasDistinctHeader ? parsedRows.slice(1) : parsedRows

  return {
    columns,
    rows: dataRows.slice(0, csvPreviewRowLimit).map((row) => padRow(row, maxColumns)),
    totalRows: dataRows.length,
    totalColumns: maxColumns,
    delimiter,
  }
}

export function decodeRtfDocument(source: string): string {
  return source
    .replace(/\\par[d]?/gu, '\n')
    .replace(/\\tab/gu, '\t')
    .replace(/\\'[0-9a-f]{2}/giu, (match) =>
      String.fromCharCode(Number.parseInt(match.slice(2), 16)),
    )
    .replace(/\\[a-z]+-?\d* ?/giu, '')
    .replace(/\\([{}\\])/gu, '$1')
    .replace(/[{}]/gu, '')
    .replace(/\r\n/gu, '\n')
    .replace(/[ \t]+\n/gu, '\n')
    .replace(/\n[ \t]+/gu, '\n')
    .replace(/\t +/gu, '\t')
    .replace(/\n{3,}/gu, '\n\n')
    .trim()
}

export function sanitizeHtmlDocument(content: string): {
  srcDoc: string
  textContent: string
  outline: ViewerDocumentOutlineItem[]
  warnings: string[]
} {
  const parser = new DOMParser()
  const documentRoot = parser.parseFromString(content, 'text/html')
  const warnings: string[] = []

  const removedDangerousNodes = documentRoot.querySelectorAll(
    'script, iframe, object, embed, meta[http-equiv="refresh"]',
  )
  if (removedDangerousNodes.length) {
    warnings.push('Из HTML preview удалены активные или потенциально опасные узлы.')
  }
  removedDangerousNodes.forEach((node) => node.remove())

  for (const element of documentRoot.querySelectorAll<HTMLElement>('*')) {
    for (const attribute of element.attributes) {
      const name = attribute.name.toLowerCase()
      const value = attribute.value.trim().toLowerCase()

      if (name.startsWith('on')) {
        element.removeAttribute(attribute.name)
        continue
      }

      if ((name === 'href' || name === 'src') && value.startsWith('javascript:')) {
        element.removeAttribute(attribute.name)
      }
    }
  }

  const outline = Array.from(documentRoot.querySelectorAll('h1, h2, h3, h4, h5, h6')).map(
    (heading, index) => ({
      id: `heading-${index + 1}`,
      label: heading.textContent?.trim() || `Heading ${index + 1}`,
      level: Number(heading.tagName.slice(1)),
    }),
  )

  const textContent = collectHtmlText(documentRoot.body).replace(/\s+/gu, ' ').trim()
  const srcDoc = [
    '<!doctype html>',
    '<html lang="en">',
    '<head>',
    '<meta charset="utf-8" />',
    '<style>',
    'html,body{margin:0;padding:0;background:#fffaf1;color:#102426;font-family:Manrope,Segoe UI,sans-serif;line-height:1.6;}',
    'body{padding:24px;}',
    'img,svg,canvas,table{max-width:100%;}',
    'table{border-collapse:collapse;}',
    'td,th{border:1px solid rgba(16,36,38,.14);padding:8px 10px;}',
    'pre,code{white-space:pre-wrap;}',
    '</style>',
    '</head>',
    `<body>${documentRoot.body.innerHTML}</body>`,
    '</html>',
  ].join('')

  return {
    srcDoc,
    textContent,
    outline,
    warnings,
  }
}

async function extractPdfSearchableText(pdfDocument: {
  numPages: number
  getPage(pageNumber: number): Promise<{
    getTextContent(): Promise<{ items: PdfJsTextItem[] }>
    cleanup(): void
  }>
}): Promise<{ text: string; warnings: string[] }> {
  const warnings: string[] = []
  const pageLimit = Math.min(pdfDocument.numPages, pdfPageSearchLimit)
  const chunks: string[] = []

  if (pdfDocument.numPages > pageLimit) {
    warnings.push(
      `PDF search layer собран только по первым ${pageLimit} страницам, чтобы viewer не зависал на больших документах.`,
    )
  }

  for (let pageNumber = 1; pageNumber <= pageLimit; pageNumber += 1) {
    const page = await pdfDocument.getPage(pageNumber)

    try {
      const textContent = await page.getTextContent()
      const pageText = textContent.items
        .map((item) => item.str?.trim() ?? '')
        .filter(Boolean)
        .join(' ')

      if (pageText) {
        chunks.push(pageText)
      }
    } finally {
      page.cleanup()
    }
  }

  return {
    text: chunks.join('\n\n'),
    warnings,
  }
}

async function loadPdfJs(): Promise<PdfJsModule> {
  pdfJsPromise ??= import('pdfjs-dist').then((module) => {
    const candidate = module as unknown as PdfJsModule
    candidate.GlobalWorkerOptions.workerSrc = pdfWorkerUrl
    return candidate
  })

  return pdfJsPromise
}

function buildTextSummary(kind: string, text: string): ViewerDocumentFact[] {
  return [
    { label: 'Тип документа', value: kind },
    { label: 'Строки', value: String(countLines(text)) },
    { label: 'Слова', value: String(countWords(text)) },
    { label: 'Символы', value: String(text.length) },
  ]
}

function splitParagraphs(text: string): string[] {
  return text
    .split(/\n{2,}/u)
    .map((paragraph) => paragraph.trim())
    .filter(Boolean)
    .slice(0, 18)
}

function readDocumentText(buffer: ArrayBuffer): string {
  const bytes = new Uint8Array(buffer)

  if (bytes[0] === 0xef && bytes[1] === 0xbb && bytes[2] === 0xbf) {
    return new TextDecoder('utf-8').decode(bytes)
  }

  if (bytes[0] === 0xff && bytes[1] === 0xfe) {
    return new TextDecoder('utf-16le').decode(bytes)
  }

  if (bytes[0] === 0xfe && bytes[1] === 0xff) {
    return new TextDecoder('utf-16be').decode(bytes)
  }

  return new TextDecoder('utf-8').decode(bytes)
}

function countLines(text: string): number {
  if (!text.length) {
    return 0
  }

  return text.split(/\r?\n/u).length
}

function countWords(text: string): number {
  const matches = text.match(/\S+/gu)
  return matches?.length ?? 0
}

function detectDelimiter(content: string): string {
  const candidates = [',', ';', '\t', '|']
  const sampleLines = content
    .split(/\r?\n/u)
    .map((line) => line.trim())
    .filter(Boolean)
    .slice(0, 6)

  const bestCandidate = candidates
    .map((candidate) => ({
      candidate,
      score: sampleLines.reduce(
        (total, line) => total + countDelimiterOutsideQuotes(line, candidate),
        0,
      ),
    }))
    .sort((left, right) => right.score - left.score)[0]

  return bestCandidate?.score ? bestCandidate.candidate : ','
}

function countDelimiterOutsideQuotes(line: string, delimiter: string): number {
  let isInsideQuotes = false
  let count = 0

  for (let index = 0; index < line.length; index += 1) {
    const character = line[index]
    const nextCharacter = line[index + 1]

    if (character === '"') {
      if (isInsideQuotes && nextCharacter === '"') {
        index += 1
      } else {
        isInsideQuotes = !isInsideQuotes
      }
      continue
    }

    if (!isInsideQuotes && character === delimiter) {
      count += 1
    }
  }

  return count
}

function parseDelimitedRows(content: string, delimiter: string): string[][] {
  const rows: string[][] = []
  let row: string[] = []
  let cell = ''
  let isInsideQuotes = false

  for (let index = 0; index < content.length; index += 1) {
    const character = content[index]
    const nextCharacter = content[index + 1]

    if (character === '"') {
      if (isInsideQuotes && nextCharacter === '"') {
        cell += '"'
        index += 1
      } else {
        isInsideQuotes = !isInsideQuotes
      }
      continue
    }

    if (!isInsideQuotes && character === delimiter) {
      row.push(cell.trim())
      cell = ''
      continue
    }

    if (!isInsideQuotes && (character === '\n' || character === '\r')) {
      if (character === '\r' && nextCharacter === '\n') {
        index += 1
      }

      row.push(cell.trim())
      rows.push(row)
      row = []
      cell = ''
      continue
    }

    cell += character ?? ''
  }

  if (cell.length || row.length) {
    row.push(cell.trim())
    rows.push(row)
  }

  return rows
}

function padRow(row: string[], size: number): string[] {
  return [...row, ...Array.from({ length: Math.max(size - row.length, 0) }, () => '')]
}

function describeDelimiter(delimiter: string): string {
  if (delimiter === '\t') {
    return 'Tab'
  }

  if (delimiter === ';') {
    return 'Semicolon'
  }

  if (delimiter === '|') {
    return 'Pipe'
  }

  return 'Comma'
}

function collectHtmlText(node: Node): string {
  if (node.nodeType === Node.TEXT_NODE) {
    return node.textContent ?? ''
  }

  const childText = Array.from(node.childNodes)
    .map((childNode) => collectHtmlText(childNode))
    .join('')

  if (!(node instanceof HTMLElement)) {
    return childText
  }

  if (/^(P|DIV|SECTION|ARTICLE|LI|TR|TD|TH|H[1-6]|BR)$/u.test(node.tagName)) {
    return `${childText} `
  }

  return childText
}
