import * as CFB from 'cfb'
import type { WorkBook, WorkSheet } from 'xlsx'
import type {
  ViewerDocumentPreviewPayload,
  ViewerDocumentSheetPreview,
  ViewerDocumentTablePreview,
} from './viewer-document'
import { buildTextSummary, normalizeExtractedText, splitParagraphs } from './viewer-document-shared'

const workbookPreviewColumnLimit = 12
const workbookPreviewRowLimit = 28
const docFieldStartMarker = String.fromCharCode(0x13)
const docFieldSeparatorMarker = String.fromCharCode(0x14)
const docFieldEndMarker = String.fromCharCode(0x15)
const docInlinePictureMarker = String.fromCharCode(0x01)
const docFloatingObjectMarker = String.fromCharCode(0x08)
const docTableCellMarker = String.fromCharCode(0x07)

let xlsxModulePromise: Promise<typeof import('xlsx')> | null = null

interface DocFib {
  base: {
    fWhichTblStm: number
  }
  fibRgLw: {
    ccpText: number
    ccpFtn: number
    ccpHdd: number
    ccpAtn: number
    ccpEdn: number
    ccpTxbx: number
    ccpHdrTxbx: number
  }
  fibRgFcLcbBlob: {
    fcClx: number
    lcbClx: number
  }
}

export async function buildDocDocumentPreview(file: File): Promise<ViewerDocumentPreviewPayload> {
  const bytes = new Uint8Array(await file.arrayBuffer())
  const text = normalizeExtractedText(extractDocText(bytes))

  return {
    summary: buildTextSummary('DOC', text),
    searchableText: text,
    warnings: [
      'Legacy DOC проходит через binary text extraction path: базовый текст сохраняется, но точная вёрстка, изображения, revision marks и таблицы Word не воспроизводятся как native layout.',
    ],
    layout: {
      mode: 'text',
      text,
      paragraphs: splitParagraphs(text),
    },
    previewLabel: 'DOC legacy text adapter',
  }
}

export async function buildXlsDocumentPreview(file: File): Promise<ViewerDocumentPreviewPayload> {
  const XLSX = await loadXlsxModule()
  const workbook = XLSX.read(new Uint8Array(await file.arrayBuffer()), {
    type: 'array',
    cellText: true,
    dense: false,
  })

  const sheets = workbook.SheetNames.map((sheetName, sheetIndex) =>
    buildWorkbookSheetPreview(XLSX, workbook, sheetName, sheetIndex),
  ).filter((sheet): sheet is ViewerDocumentSheetPreview => sheet !== null)

  if (!sheets.length) {
    throw new Error('XLS adapter не нашёл ни одного рабочего листа внутри книги.')
  }

  const searchableText = sheets
    .map((sheet) =>
      [sheet.name, ...sheet.table.rows.map((row) => row.filter(Boolean).join(' '))].join('\n'),
    )
    .join('\n\n')

  return {
    summary: [
      { label: 'Тип документа', value: 'XLS' },
      { label: 'Sheets', value: String(sheets.length) },
      { label: 'Rows', value: String(sheets[0]?.table.totalRows ?? 0) },
      { label: 'Columns', value: String(sheets[0]?.table.totalColumns ?? 0) },
    ],
    searchableText,
    warnings: [
      'Legacy XLS декодируется через workbook adapter: viewer нормализует sheet data, но не воспроизводит формулы, визуальные стили, диаграммы и макросы как Excel layout.',
    ],
    layout: {
      mode: 'workbook',
      text: searchableText,
      sheets,
      activeSheetIndex: 0,
    },
    previewLabel: 'XLS legacy workbook adapter',
  }
}

async function loadXlsxModule(): Promise<typeof import('xlsx')> {
  xlsxModulePromise ??= import('xlsx').then(async (module) => {
    try {
      // `xlsx` не публикует типы для optional codepage helper, хотя сам файл
      // нужен, чтобы legacy XLS нормально переживал старые encodings.
      const codePageModule = await import('xlsx/dist/cpexcel.full.mjs')
      module.set_cptable(codePageModule)
    } catch {
      // Для части XLS хватит и базового unicode path; отсутствие codepage-таблиц
      // не должно ломать viewer целиком.
    }

    return module
  })

  return xlsxModulePromise
}

function buildWorkbookSheetPreview(
  XLSX: typeof import('xlsx'),
  workbook: WorkBook,
  sheetName: string,
  sheetIndex: number,
): ViewerDocumentSheetPreview | null {
  const worksheet = workbook.Sheets[sheetName]

  if (!worksheet) {
    return null
  }

  return {
    id: `xls-sheet-${sheetIndex + 1}`,
    name: sheetName,
    table: buildWorksheetTable(XLSX, worksheet),
  }
}

function buildWorksheetTable(
  XLSX: typeof import('xlsx'),
  worksheet: WorkSheet,
): ViewerDocumentTablePreview {
  const rows = XLSX.utils.sheet_to_json(worksheet, {
    header: 1,
    raw: false,
    blankrows: false,
    defval: '',
  }) as string[][]

  const normalizedRows = rows
    .map((row) => row.map((cell) => String(cell ?? '').trimEnd()))
    .filter((row) => row.some((cell) => cell.length > 0))

  if (!normalizedRows.length) {
    return {
      columns: [],
      rows: [],
      totalRows: 0,
      totalColumns: 0,
      delimiter: '',
    }
  }

  const maxColumns = Math.max(...normalizedRows.map((row) => row.length), 0)
  const headerRow = normalizedRows[0] ?? []
  const columns = Array.from({ length: maxColumns }, (_, columnIndex) => {
    const value = headerRow[columnIndex]?.trim()
    return value || toSpreadsheetColumnLabel(columnIndex)
  })

  return {
    columns: columns.slice(0, workbookPreviewColumnLimit),
    rows: normalizedRows
      .slice(1, workbookPreviewRowLimit + 1)
      .map((row) => padRow(row, maxColumns).slice(0, workbookPreviewColumnLimit)),
    totalRows: Math.max(normalizedRows.length - 1, 0),
    totalColumns: maxColumns,
    delimiter: '',
  }
}

function padRow(row: string[], size: number): string[] {
  return [...row, ...Array.from({ length: Math.max(size - row.length, 0) }, () => '')]
}

function toSpreadsheetColumnLabel(columnIndex: number): string {
  let value = columnIndex + 1
  let label = ''

  while (value > 0) {
    const remainder = (value - 1) % 26
    label = String.fromCharCode(65 + remainder) + label
    value = Math.floor((value - 1) / 26)
  }

  return label
}

function extractDocText(bytes: Uint8Array): string {
  const container = CFB.read(bytes, { type: 'array' })
  const wordDocument = CFB.find(container, '/WordDocument')

  if (!wordDocument?.content) {
    throw new Error('DOC adapter не нашёл поток WordDocument внутри CFB-контейнера.')
  }

  const wordStream = toUint8Array(wordDocument.content)
  const fib = readFib(wordStream)
  const tableName = fib.base.fWhichTblStm === 1 ? '/1Table' : '/0Table'
  const tableEntry = CFB.find(container, tableName)

  if (!tableEntry?.content) {
    throw new Error(`DOC adapter не нашёл обязательный поток ${tableName} внутри CFB-контейнера.`)
  }

  const tableStream = toUint8Array(tableEntry.content)
  const clx = tableStream.slice(
    fib.fibRgFcLcbBlob.fcClx,
    fib.fibRgFcLcbBlob.fcClx + fib.fibRgFcLcbBlob.lcbClx,
  )
  const plcPcd = parseClx(clx)
  let text = getDocText(fib.fibRgLw, plcPcd, wordStream)

  // Word binary использует управляющие маркеры для полей, inline-объектов и границ
  // ячеек. Здесь вычищаем их, чтобы search layer не тонул в служебном шуме.
  text = text.replace(
    new RegExp(
      `${docFieldStartMarker}.*?${docFieldSeparatorMarker}(.*?)${docFieldEndMarker}`,
      'gsu',
    ),
    '$1',
  )
  text = text.replace(new RegExp(`${docFieldStartMarker}.*?${docFieldEndMarker}`, 'gsu'), '')
  text = text.replace(
    new RegExp(`[${docInlinePictureMarker}${docFloatingObjectMarker}]`, 'gu'),
    '',
  )
  text = text.replace(new RegExp(docTableCellMarker, 'gu'), '\r')

  return text
}

function readFib(bytes: Uint8Array): DocFib {
  let offset = 0
  const base = readFibBase(bytes.slice(offset, (offset += 32)))
  offset += 32
  const fibRgLw = readFibRgLw(bytes.slice(offset, (offset += 88)))
  const cbRgFcLcb = readUInt16LE(bytes, offset)
  offset += 2
  const fibRgFcLcbBlob = readFibRgFcLcbBlob(bytes.slice(offset, offset + cbRgFcLcb * 8))

  return {
    base,
    fibRgLw,
    fibRgFcLcbBlob,
  }
}

function readFibBase(bytes: Uint8Array): DocFib['base'] {
  const bits = readUInt8(bytes, 11)

  return {
    fWhichTblStm: (bits >> 1) & 0x1,
  }
}

function readFibRgLw(bytes: Uint8Array): DocFib['fibRgLw'] {
  return {
    ccpText: readInt32LE(bytes, 12),
    ccpFtn: readInt32LE(bytes, 16),
    ccpHdd: readInt32LE(bytes, 20),
    ccpAtn: readInt32LE(bytes, 28),
    ccpEdn: readInt32LE(bytes, 32),
    ccpTxbx: readInt32LE(bytes, 36),
    ccpHdrTxbx: readInt32LE(bytes, 40),
  }
}

function readFibRgFcLcbBlob(bytes: Uint8Array): DocFib['fibRgFcLcbBlob'] {
  const offset = 33 * 8

  return {
    fcClx: readUInt32LE(bytes, offset),
    lcbClx: readUInt32LE(bytes, offset + 4),
  }
}

function parseClx(bytes: Uint8Array): Uint8Array {
  let offset = 0

  while (readUInt8(bytes, offset) === 0x1) {
    offset += 1
    const cbGrpGpl = readUInt16LE(bytes, offset)
    offset += 2 + cbGrpGpl
  }

  if (readUInt8(bytes, offset) !== 0x2) {
    throw new Error('DOC adapter не смог прочитать CLX-структуру документа.')
  }

  const lcb = readUInt32LE(bytes, offset + 1)
  return bytes.slice(offset + 5, offset + 5 + lcb)
}

function getDocText(fibRgLw: DocFib['fibRgLw'], plcPcd: Uint8Array, documentStream: Uint8Array): string {
  const lastCp = getLastCp(fibRgLw)
  let offset = 0
  let pcdCount = -1

  while (readUInt32LE(plcPcd, offset) <= lastCp) {
    offset += 4
    pcdCount += 1
  }

  const acp = plcPcd.slice(0, offset)
  const upperBound = offset + pcdCount * 8
  let acpIndex = 0
  let text = ''

  while (offset < upperBound) {
    const pcd = plcPcd.slice(offset, (offset += 8))
    const fcCompressed = readUInt32LE(pcd, 2)
    const fc = fcCompressed & ~(0x1 << 30)
    const stringLength = readUInt32LE(acp, (acpIndex + 1) * 4) - readUInt32LE(acp, acpIndex * 4)

    text +=
      (fcCompressed >> 30) & 0x1
        ? getCompressedDocText(documentStream, fc, stringLength)
        : getUncompressedDocText(documentStream, fc, stringLength)
    acpIndex += 1
  }

  return text.length === fibRgLw.ccpText ? text.slice(0, -1) : text.slice(0, fibRgLw.ccpText)
}

function getLastCp(fibRgLw: DocFib['fibRgLw']): number {
  const otherCounts = [
    fibRgLw.ccpFtn,
    fibRgLw.ccpHdd,
    fibRgLw.ccpAtn,
    fibRgLw.ccpEdn,
    fibRgLw.ccpTxbx,
    fibRgLw.ccpHdrTxbx,
  ]
  const otherSum = otherCounts.reduce((total, value) => total + value, 0)
  return otherSum !== 0 ? otherSum + fibRgLw.ccpText + 1 : fibRgLw.ccpText
}

function getCompressedDocText(bytes: Uint8Array, fc: number, stringLength: number): string {
  const slice = bytes.slice(fc / 2, fc / 2 + stringLength)
  return fixCompressedDocString(
    Array.from(slice, (byte) => String.fromCharCode(byte)).join(''),
  )
}

function getUncompressedDocText(bytes: Uint8Array, fc: number, stringLength: number): string {
  return new TextDecoder('utf-16le').decode(bytes.slice(fc, fc + stringLength * 2))
}

function fixCompressedDocString(value: string): string {
  const replacements: Record<string, string> = {
    '\x82': '\u201A',
    '\x83': '\u0192',
    '\x84': '\u201E',
    '\x85': '\u2026',
    '\x86': '\u2020',
    '\x87': '\u2021',
    '\x88': '\u02C6',
    '\x89': '\u2030',
    '\x8a': '\u0160',
    '\x8b': '\u2039',
    '\x8c': '\u0152',
    '\x91': '\u2018',
    '\x92': '\u2019',
    '\x93': '\u201C',
    '\x94': '\u201D',
    '\x95': '\u2022',
    '\x96': '\u2013',
    '\x97': '\u2014',
    '\x98': '\u02DC',
    '\x99': '\u2122',
    '\x9a': '\u0161',
    '\x9b': '\u203A',
    '\x9c': '\u0153',
    '\x9f': '\u0178',
  }

  return value.replace(/[\x82-\x8c\x91-\x9c\x9f]/giu, (match) => replacements[match] ?? match)
}

function toUint8Array(value: Uint8Array | number[]): Uint8Array {
  return value instanceof Uint8Array ? value : Uint8Array.from(value)
}

function readUInt8(bytes: Uint8Array, offset: number): number {
  return bytes[offset] ?? 0
}

function readUInt16LE(bytes: Uint8Array, offset: number): number {
  return new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength).getUint16(offset, true)
}

function readUInt32LE(bytes: Uint8Array, offset: number): number {
  return new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength).getUint32(offset, true)
}

function readInt32LE(bytes: Uint8Array, offset: number): number {
  return new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength).getInt32(offset, true)
}
