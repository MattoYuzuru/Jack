import pdfWorkerUrl from 'pdfjs-dist/build/pdf.worker.mjs?url'
import type {
  ViewerDocumentFact,
  ViewerDocumentOutlineItem,
  ViewerDocumentPreviewPayload,
  ViewerDocumentSlidePreview,
  ViewerDocumentTablePreview,
} from './viewer-document'
import {
  escapeHtml,
  findFirstXmlChild,
  findFirstXmlDescendant,
  findXmlChildren,
  findXmlDescendants,
  loadViewerOoxmlPackage,
  readOoxmlRelationships,
  readXmlAttribute,
  resolveOoxmlPath,
  wrapViewerDocumentHtml,
} from './viewer-ooxml'

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

interface ViewerDocxBlock {
  kind: 'heading' | 'paragraph' | 'table'
  level?: number
  text?: string
  rows?: string[][]
}

const pdfPageSearchLimit = 12
const csvPreviewRowLimit = 24
const workbookPreviewColumnLimit = 12
const workbookPreviewRowLimit = 28

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

export async function buildDocxDocumentPreview(file: File): Promise<ViewerDocumentPreviewPayload> {
  const ooxml = await loadViewerOoxmlPackage(file)
  const documentRoot = await ooxml.readXml('word/document.xml')

  if (!documentRoot) {
    throw new Error('DOCX adapter не нашёл word/document.xml внутри контейнера.')
  }

  const body = findFirstXmlDescendant(documentRoot, 'body')
  if (!body) {
    throw new Error('DOCX adapter не нашёл body-узел документа.')
  }

  const blocks = parseDocxBlocks(body)
  const paragraphs = blocks
    .filter((block): block is ViewerDocxBlock & { text: string } => typeof block.text === 'string')
    .map((block) => block.text)
  const outline = blocks
    .filter((block): block is ViewerDocxBlock & { text: string; level: number } =>
      block.kind === 'heading' && typeof block.text === 'string' && typeof block.level === 'number',
    )
    .map((block, index) => ({
      id: `docx-heading-${index + 1}`,
      label: block.text,
      level: block.level,
    }))
  const searchableText = paragraphs.join('\n\n')
  const srcDoc = wrapViewerDocumentHtml(renderDocxBlocks(blocks))
  const tableCount = blocks.filter((block) => block.kind === 'table').length

  return {
    summary: [
      { label: 'Тип документа', value: 'DOCX' },
      { label: 'Блоки', value: String(blocks.length) },
      { label: 'Headings', value: String(outline.length) },
      { label: 'Таблицы', value: String(tableCount) },
    ],
    searchableText,
    warnings: [
      'DOCX preview пока не воспроизводит точную вёрстку Word: сложные стили, изображения и колонтитулы сводятся к упрощённому document layer.',
    ],
    layout: {
      mode: 'html',
      text: searchableText,
      srcDoc,
      outline,
    },
    previewLabel: 'DOCX OOXML adapter',
  }
}

export async function buildXlsxDocumentPreview(file: File): Promise<ViewerDocumentPreviewPayload> {
  const ooxml = await loadViewerOoxmlPackage(file)
  const workbookRoot = await ooxml.readXml('xl/workbook.xml')

  if (!workbookRoot) {
    throw new Error('XLSX adapter не нашёл xl/workbook.xml внутри контейнера.')
  }

  const workbookRelsRoot = await ooxml.readXml('xl/_rels/workbook.xml.rels')
  const workbookRelations = workbookRelsRoot ? readOoxmlRelationships(workbookRelsRoot) : new Map()
  const sharedStrings = await readSharedStrings(ooxml)
  const sheetNodes = findXmlDescendants(workbookRoot, 'sheet')
  const sheets = []

  for (const [index, sheetNode] of sheetNodes.entries()) {
    const relationId = readXmlAttribute(sheetNode, 'r:id')
    const target = relationId ? workbookRelations.get(relationId) : null
    const sheetName = readXmlAttribute(sheetNode, 'name') ?? `Sheet ${index + 1}`

    if (!target) {
      continue
    }

    const sheetRoot = await ooxml.readXml(resolveOoxmlPath('xl/workbook.xml', target))
    if (!sheetRoot) {
      continue
    }

    sheets.push({
      id: `sheet-${index + 1}`,
      name: sheetName,
      table: parseWorksheetTable(sheetRoot, sharedStrings),
    })
  }

  if (!sheets.length) {
    throw new Error('XLSX adapter не нашёл ни одного sheet внутри workbook.')
  }

  const searchableText = sheets
    .map((sheet) =>
      [sheet.name, ...sheet.table.rows.map((row) => row.filter(Boolean).join(' '))].join('\n'),
    )
    .join('\n\n')

  return {
    summary: [
      { label: 'Тип документа', value: 'XLSX' },
      { label: 'Sheets', value: String(sheets.length) },
      { label: 'Rows', value: String(sheets[0]?.table.totalRows ?? 0) },
      { label: 'Columns', value: String(sheets[0]?.table.totalColumns ?? 0) },
    ],
    searchableText,
    warnings: [
      'XLSX preview показывает sheet data, но не воспроизводит formulas, styles, merged cells и charts как native spreadsheet layout.',
    ],
    layout: {
      mode: 'workbook',
      text: searchableText,
      sheets,
      activeSheetIndex: 0,
    },
    previewLabel: 'XLSX workbook adapter',
  }
}

export async function buildPptxDocumentPreview(file: File): Promise<ViewerDocumentPreviewPayload> {
  const ooxml = await loadViewerOoxmlPackage(file)
  const presentationRoot = await ooxml.readXml('ppt/presentation.xml')

  if (!presentationRoot) {
    throw new Error('PPTX adapter не нашёл ppt/presentation.xml внутри контейнера.')
  }

  const presentationRelsRoot = await ooxml.readXml('ppt/_rels/presentation.xml.rels')
  const presentationRelations = presentationRelsRoot
    ? readOoxmlRelationships(presentationRelsRoot)
    : new Map()
  const slideNodes = findXmlDescendants(presentationRoot, 'sldId')
  const slides: ViewerDocumentSlidePreview[] = []

  for (const [index, slideNode] of slideNodes.entries()) {
    const relationId = readXmlAttribute(slideNode, 'r:id')
    const target = relationId ? presentationRelations.get(relationId) : null

    if (!target) {
      continue
    }

    const slideRoot = await ooxml.readXml(resolveOoxmlPath('ppt/presentation.xml', target))
    if (!slideRoot) {
      continue
    }

    const texts = findXmlDescendants(slideRoot, 't')
      .map((element) => element.textContent?.trim() ?? '')
      .filter(Boolean)

    slides.push({
      id: `slide-${index + 1}`,
      title: texts[0] ?? `Slide ${index + 1}`,
      bullets: texts.slice(1),
    })
  }

  if (!slides.length) {
    throw new Error('PPTX adapter не нашёл ни одного slide внутри presentation.')
  }

  const searchableText = slides
    .map((slide) => [slide.title, ...slide.bullets].join('\n'))
    .join('\n\n')

  return {
    summary: [
      { label: 'Тип документа', value: 'PPTX' },
      { label: 'Слайды', value: String(slides.length) },
      { label: 'Text shapes', value: String(slides.reduce((total, slide) => total + slide.bullets.length + 1, 0)) },
      { label: 'Preview mode', value: 'Slide text deck' },
    ],
    searchableText,
    warnings: [
      'PPTX preview сейчас показывает text deck без точной композиции, background assets, animations и speaker notes.',
    ],
    layout: {
      mode: 'slides',
      text: searchableText,
      slides,
    },
    previewLabel: 'PPTX slide adapter',
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

  return {
    srcDoc: wrapViewerDocumentHtml(documentRoot.body.innerHTML),
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

function parseDocxBlocks(body: Element): ViewerDocxBlock[] {
  const blocks: ViewerDocxBlock[] = []

  for (const child of Array.from(body.children)) {
    if (child.localName === 'p') {
      const paragraph = parseDocxParagraph(child)
      if (paragraph) {
        blocks.push(paragraph)
      }
      continue
    }

    if (child.localName === 'tbl') {
      const rows = parseDocxTable(child)
      if (rows.length) {
        blocks.push({
          kind: 'table',
          rows,
        })
      }
    }
  }

  return blocks
}

function parseDocxParagraph(node: Element): ViewerDocxBlock | null {
  const texts: string[] = []

  for (const child of findXmlDescendants(node, 't')) {
    const value = child.textContent ?? ''
    if (value) {
      texts.push(value)
    }
  }

  for (const tabNode of findXmlDescendants(node, 'tab')) {
    if (tabNode) {
      texts.push('\t')
    }
  }

  const rawText = texts.join('').replace(/\s+/gu, ' ').trim()
  if (!rawText) {
    return null
  }

  const styleNode = findFirstXmlDescendant(node, 'pStyle')
  const styleValue = styleNode ? readXmlAttribute(styleNode, 'val') : null
  const headingMatch = styleValue?.match(/Heading([1-6])/iu)

  if (headingMatch) {
    return {
      kind: 'heading',
      level: Number(headingMatch[1]),
      text: rawText,
    }
  }

  const hasNumbering = Boolean(findFirstXmlDescendant(node, 'numPr'))
  return {
    kind: 'paragraph',
    text: hasNumbering ? `• ${rawText}` : rawText,
  }
}

function parseDocxTable(node: Element): string[][] {
  const rows = []

  for (const rowNode of findXmlChildren(node, 'tr')) {
    const cells = findXmlChildren(rowNode, 'tc')
      .map((cellNode) =>
        findXmlDescendants(cellNode, 't')
          .map((textNode) => textNode.textContent?.trim() ?? '')
          .filter(Boolean)
          .join(' '),
      )
      .filter((cellValue, index, values) => cellValue.length > 0 || index < values.length)

    if (cells.length) {
      rows.push(cells)
    }
  }

  return rows
}

function renderDocxBlocks(blocks: ViewerDocxBlock[]): string {
  return blocks
    .map((block) => {
      if (block.kind === 'heading') {
        return `<h${block.level}>${escapeHtml(block.text ?? '')}</h${block.level}>`
      }

      if (block.kind === 'table') {
        const rows = block.rows ?? []
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

      const text = block.text ?? ''
      if (text.startsWith('• ')) {
        return `<ul class="docx-list"><li>${escapeHtml(text.slice(2))}</li></ul>`
      }

      return `<p>${escapeHtml(text)}</p>`
    })
    .join('')
}

async function readSharedStrings(
  ooxml: Awaited<ReturnType<typeof loadViewerOoxmlPackage>>,
): Promise<string[]> {
  const sharedStringsRoot = await ooxml.readXml('xl/sharedStrings.xml')
  if (!sharedStringsRoot) {
    return []
  }

  return findXmlDescendants(sharedStringsRoot, 'si').map((sharedStringNode) =>
    findXmlDescendants(sharedStringNode, 't')
      .map((textNode) => textNode.textContent ?? '')
      .join(''),
  )
}

function parseWorksheetTable(sheetRoot: XMLDocument, sharedStrings: string[]): ViewerDocumentTablePreview {
  const matrix = new Map<number, Map<number, string>>()
  let maxRow = 0
  let maxColumn = 0

  for (const rowNode of findXmlDescendants(sheetRoot, 'row')) {
    const rowIndex = Number(readXmlAttribute(rowNode, 'r') ?? '0')

    for (const cellNode of findXmlChildren(rowNode, 'c')) {
      const cellReference = readXmlAttribute(cellNode, 'r') ?? ''
      const columnIndex = referenceToColumnIndex(cellReference)
      const value = parseWorksheetCellValue(cellNode, sharedStrings)

      if (columnIndex < 0 || !value.trim()) {
        continue
      }

      const targetRow = rowIndex > 0 ? rowIndex - 1 : matrix.size
      const rowMap = matrix.get(targetRow) ?? new Map<number, string>()
      rowMap.set(columnIndex, value)
      matrix.set(targetRow, rowMap)
      maxRow = Math.max(maxRow, targetRow + 1)
      maxColumn = Math.max(maxColumn, columnIndex + 1)
    }
  }

  const totalColumns = maxColumn
  const totalRows = Math.max(maxRow - 1, 0)
  const columns = buildWorksheetColumns(matrix.get(0), totalColumns)
  const rows = Array.from({ length: Math.min(totalRows, workbookPreviewRowLimit) }, (_, rowIndex) =>
    buildWorksheetRow(matrix.get(rowIndex + 1), totalColumns),
  ).map((row) => row.slice(0, workbookPreviewColumnLimit))

  return {
    columns: columns.slice(0, workbookPreviewColumnLimit),
    rows,
    totalRows,
    totalColumns,
    delimiter: '',
  }
}

function buildWorksheetColumns(
  headerRow: Map<number, string> | undefined,
  totalColumns: number,
): string[] {
  return Array.from({ length: totalColumns }, (_, columnIndex) => {
    const value = headerRow?.get(columnIndex)?.trim()
    return value || toSpreadsheetColumnLabel(columnIndex)
  })
}

function buildWorksheetRow(row: Map<number, string> | undefined, totalColumns: number): string[] {
  return Array.from({ length: totalColumns }, (_, columnIndex) => row?.get(columnIndex) ?? '')
}

function parseWorksheetCellValue(cellNode: Element, sharedStrings: string[]): string {
  const cellType = readXmlAttribute(cellNode, 't')

  if (cellType === 'inlineStr') {
    return findXmlDescendants(cellNode, 't')
      .map((textNode) => textNode.textContent ?? '')
      .join('')
      .trim()
  }

  const valueNode = findFirstXmlChild(cellNode, 'v')
  const rawValue = valueNode?.textContent?.trim() ?? ''

  if (!rawValue) {
    return ''
  }

  if (cellType === 's') {
    const sharedStringIndex = Number.parseInt(rawValue, 10)
    return sharedStrings[sharedStringIndex] ?? rawValue
  }

  if (cellType === 'b') {
    return rawValue === '1' ? 'TRUE' : 'FALSE'
  }

  return rawValue
}

function referenceToColumnIndex(reference: string): number {
  const columnLabel = reference.match(/^[A-Z]+/iu)?.[0]?.toUpperCase()
  if (!columnLabel) {
    return -1
  }

  let value = 0

  for (const character of columnLabel) {
    value = value * 26 + (character.charCodeAt(0) - 64)
  }

  return value - 1
}

function toSpreadsheetColumnLabel(index: number): string {
  let value = index + 1
  let label = ''

  while (value > 0) {
    const remainder = (value - 1) % 26
    label = String.fromCharCode(65 + remainder) + label
    value = Math.floor((value - 1) / 26)
  }

  return label
}
