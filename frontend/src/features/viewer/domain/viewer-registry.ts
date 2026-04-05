export type ViewerFormatFamily = 'image' | 'document' | 'media' | 'data'

export type ViewerPreviewPipeline = 'browser-native' | 'client-decode' | 'planned'

export type PreviewStrategyId =
  | 'native-image'
  | 'heic-image'
  | 'tiff-image'
  | 'raw-image'
  | 'pdf-document'
  | 'text-document'
  | 'csv-document'
  | 'html-document'
  | 'rtf-document'
  | 'docx-document'
  | 'xlsx-document'
  | 'pptx-document'
  | 'planned-document'

export interface ViewerFormatDefinition {
  extension: string
  aliases: string[]
  label: string
  family: ViewerFormatFamily
  mimeTypes: string[]
  previewPipeline: ViewerPreviewPipeline
  previewStrategyId: PreviewStrategyId
  statusLabel: string
  notes: string
  accents: string[]
}

const imageFormatDefinitions: ViewerFormatDefinition[] = [
  {
    extension: 'jpg',
    aliases: [],
    label: 'JPG',
    family: 'image',
    mimeTypes: ['image/jpeg'],
    previewPipeline: 'browser-native',
    previewStrategyId: 'native-image',
    statusLabel: 'Browser preview',
    notes: 'Нативный browser-first сценарий без промежуточной конвертации.',
    accents: ['Raster', 'Fast path'],
  },
  {
    extension: 'jpeg',
    aliases: [],
    label: 'JPEG',
    family: 'image',
    mimeTypes: ['image/jpeg'],
    previewPipeline: 'browser-native',
    previewStrategyId: 'native-image',
    statusLabel: 'Browser preview',
    notes: 'Использует тот же быстрый preview path, что и JPG.',
    accents: ['Raster', 'Alias'],
  },
  {
    extension: 'png',
    aliases: [],
    label: 'PNG',
    family: 'image',
    mimeTypes: ['image/png'],
    previewPipeline: 'browser-native',
    previewStrategyId: 'native-image',
    statusLabel: 'Browser preview',
    notes: 'Прозрачность и lossless-контент рендерятся прямо в viewport.',
    accents: ['Raster', 'Alpha'],
  },
  {
    extension: 'webp',
    aliases: [],
    label: 'WebP',
    family: 'image',
    mimeTypes: ['image/webp'],
    previewPipeline: 'browser-native',
    previewStrategyId: 'native-image',
    statusLabel: 'Browser preview',
    notes: 'Оптимальный сценарий для современного browser preview.',
    accents: ['Modern', 'Compressed'],
  },
  {
    extension: 'avif',
    aliases: [],
    label: 'AVIF',
    family: 'image',
    mimeTypes: ['image/avif'],
    previewPipeline: 'browser-native',
    previewStrategyId: 'native-image',
    statusLabel: 'Browser preview',
    notes: 'В современных браузерах открывается напрямую, без дополнительного decode-шага.',
    accents: ['Modern', 'High efficiency'],
  },
  {
    extension: 'gif',
    aliases: [],
    label: 'GIF',
    family: 'image',
    mimeTypes: ['image/gif'],
    previewPipeline: 'browser-native',
    previewStrategyId: 'native-image',
    statusLabel: 'Browser preview',
    notes: 'Анимация остаётся в исходном виде, а viewport управляет масштабом и вращением.',
    accents: ['Animated', 'Legacy'],
  },
  {
    extension: 'bmp',
    aliases: [],
    label: 'BMP',
    family: 'image',
    mimeTypes: ['image/bmp'],
    previewPipeline: 'browser-native',
    previewStrategyId: 'native-image',
    statusLabel: 'Browser preview',
    notes: 'Подходит для прямого просмотра и дальнейшего перехода в конвертацию.',
    accents: ['Raster', 'Large'],
  },
  {
    extension: 'svg',
    aliases: [],
    label: 'SVG',
    family: 'image',
    mimeTypes: ['image/svg+xml'],
    previewPipeline: 'browser-native',
    previewStrategyId: 'native-image',
    statusLabel: 'Browser preview',
    notes: 'Векторный preview остаётся резким на любом масштабе.',
    accents: ['Vector', 'Sharp'],
  },
  {
    extension: 'ico',
    aliases: [],
    label: 'ICO',
    family: 'image',
    mimeTypes: ['image/x-icon', 'image/vnd.microsoft.icon'],
    previewPipeline: 'browser-native',
    previewStrategyId: 'native-image',
    statusLabel: 'Browser preview',
    notes: 'Иконки открываются напрямую и пригодны для быстрой инспекции.',
    accents: ['Icon', 'Multi-size'],
  },
  {
    extension: 'heic',
    aliases: ['heif'],
    label: 'HEIC',
    family: 'image',
    mimeTypes: ['image/heic', 'image/heif'],
    previewPipeline: 'client-decode',
    previewStrategyId: 'heic-image',
    statusLabel: 'Decode adapter',
    notes:
      'Файл декодируется в web-friendly raster прямо в клиенте через отдельную decode-стратегию.',
    accents: ['Apple', 'Decode'],
  },
  {
    extension: 'tiff',
    aliases: ['tif'],
    label: 'TIFF',
    family: 'image',
    mimeTypes: ['image/tiff'],
    previewPipeline: 'client-decode',
    previewStrategyId: 'tiff-image',
    statusLabel: 'Decode adapter',
    notes:
      'Многостраничные и сжатые TIFF рендерятся через отдельный decode-layer с переводом в PNG preview.',
    accents: ['Archive', 'Decode'],
  },
  {
    extension: 'raw',
    aliases: ['dng', 'cr2', 'cr3', 'nef', 'arw', 'raf', 'rw2', 'orf', 'pef', 'srw'],
    label: 'RAW',
    family: 'image',
    mimeTypes: [],
    previewPipeline: 'client-decode',
    previewStrategyId: 'raw-image',
    statusLabel: 'Decode adapter',
    notes:
      'RAW family идёт через TIFF-ish adapter: viewer ищет renderable preview/IFD и поднимает metadata без деградации остальных форматов.',
    accents: ['Camera', 'Preview extraction'],
  },
]

const documentFormatDefinitions: ViewerFormatDefinition[] = [
  {
    extension: 'pdf',
    aliases: [],
    label: 'PDF',
    family: 'document',
    mimeTypes: ['application/pdf'],
    previewPipeline: 'browser-native',
    previewStrategyId: 'pdf-document',
    statusLabel: 'Browser document',
    notes:
      'PDF остаётся в browser embed preview, но viewer дополнительно поднимает page stats и search layer через document runtime.',
    accents: ['Pages', 'Search layer'],
  },
  {
    extension: 'txt',
    aliases: [],
    label: 'TXT',
    family: 'document',
    mimeTypes: ['text/plain'],
    previewPipeline: 'client-decode',
    previewStrategyId: 'text-document',
    statusLabel: 'Text decode',
    notes: 'Plain text читается прямо в клиенте и получает search-панель без отдельного viewer-слоя.',
    accents: ['Text', 'Search'],
  },
  {
    extension: 'csv',
    aliases: [],
    label: 'CSV',
    family: 'document',
    mimeTypes: ['text/csv', 'application/csv'],
    previewPipeline: 'client-decode',
    previewStrategyId: 'csv-document',
    statusLabel: 'Table decode',
    notes:
      'CSV поднимается как tabular preview со строками, колонками, delimiter summary и quick find.',
    accents: ['Table', 'Search'],
  },
  {
    extension: 'html',
    aliases: ['htm'],
    label: 'HTML',
    family: 'document',
    mimeTypes: ['text/html'],
    previewPipeline: 'client-decode',
    previewStrategyId: 'html-document',
    statusLabel: 'Sandbox preview',
    notes:
      'HTML рендерится внутри sandbox iframe после безопасной очистки активного контента и сбора outline.',
    accents: ['Sandbox', 'Outline'],
  },
  {
    extension: 'rtf',
    aliases: [],
    label: 'RTF',
    family: 'document',
    mimeTypes: ['application/rtf', 'text/rtf'],
    previewPipeline: 'client-decode',
    previewStrategyId: 'rtf-document',
    statusLabel: 'Text extraction',
    notes:
      'RTF пока сводится в текстовый слой с поиском и статистикой, без faithful layout render.',
    accents: ['Legacy', 'Search'],
  },
  {
    extension: 'doc',
    aliases: [],
    label: 'DOC',
    family: 'document',
    mimeTypes: ['application/msword'],
    previewPipeline: 'planned',
    previewStrategyId: 'planned-document',
    statusLabel: 'Foundation only',
    notes: 'Слот формата уже есть в registry, но для бинарного Word нужен отдельный parser/render pipeline.',
    accents: ['Word', 'Planned'],
  },
  {
    extension: 'docx',
    aliases: [],
    label: 'DOCX',
    family: 'document',
    mimeTypes: ['application/vnd.openxmlformats-officedocument.wordprocessingml.document'],
    previewPipeline: 'client-decode',
    previewStrategyId: 'docx-document',
    statusLabel: 'OOXML adapter',
    notes:
      'DOCX проходит через OOXML container parser: viewer поднимает headings, tables, текстовый слой и упрощённый document HTML preview.',
    accents: ['Word', 'OOXML'],
  },
  {
    extension: 'odt',
    aliases: [],
    label: 'ODT',
    family: 'document',
    mimeTypes: ['application/vnd.oasis.opendocument.text'],
    previewPipeline: 'planned',
    previewStrategyId: 'planned-document',
    statusLabel: 'Foundation only',
    notes: 'ODT будет добавлен поверх того же document contract, но требует отдельного archive parser.',
    accents: ['OpenDocument', 'Planned'],
  },
  {
    extension: 'xls',
    aliases: [],
    label: 'XLS',
    family: 'document',
    mimeTypes: ['application/vnd.ms-excel'],
    previewPipeline: 'planned',
    previewStrategyId: 'planned-document',
    statusLabel: 'Foundation only',
    notes: 'Legacy Excel пока только описан в capability map; для него нужен свой workbook decode path.',
    accents: ['Spreadsheet', 'Legacy'],
  },
  {
    extension: 'xlsx',
    aliases: [],
    label: 'XLSX',
    family: 'document',
    mimeTypes: ['application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'],
    previewPipeline: 'client-decode',
    previewStrategyId: 'xlsx-document',
    statusLabel: 'OOXML adapter',
    notes:
      'XLSX поднимается как workbook preview: sheets, первая табличная проекция, search layer и summary по структуре книги.',
    accents: ['Spreadsheet', 'OOXML'],
  },
  {
    extension: 'pptx',
    aliases: [],
    label: 'PPTX',
    family: 'document',
    mimeTypes: ['application/vnd.openxmlformats-officedocument.presentationml.presentation'],
    previewPipeline: 'client-decode',
    previewStrategyId: 'pptx-document',
    statusLabel: 'OOXML adapter',
    notes:
      'PPTX идёт через slide-aware text adapter: viewer строит deck summary, slide titles и searchable text layer.',
    accents: ['Slides', 'OOXML'],
  },
  {
    extension: 'epub',
    aliases: [],
    label: 'EPUB',
    family: 'document',
    mimeTypes: ['application/epub+zip'],
    previewPipeline: 'planned',
    previewStrategyId: 'planned-document',
    statusLabel: 'Foundation only',
    notes: 'EPUB заведён в capability map как будущий reflowable-document сценарий.',
    accents: ['Book', 'Planned'],
  },
  {
    extension: 'db',
    aliases: [],
    label: 'DB',
    family: 'document',
    mimeTypes: [],
    previewPipeline: 'planned',
    previewStrategyId: 'planned-document',
    statusLabel: 'Foundation only',
    notes: 'Database container распознан в registry, но отдельный data-viewer будет подключён позже.',
    accents: ['Data', 'Planned'],
  },
  {
    extension: 'sqlite',
    aliases: [],
    label: 'SQLite',
    family: 'document',
    mimeTypes: ['application/vnd.sqlite3'],
    previewPipeline: 'planned',
    previewStrategyId: 'planned-document',
    statusLabel: 'Foundation only',
    notes: 'SQLite позже пойдёт в table-aware viewer с introspection схемы и query-safe preview.',
    accents: ['Data', 'Database'],
  },
]

const registry = [...imageFormatDefinitions, ...documentFormatDefinitions]

const formatByExtension = new Map<string, ViewerFormatDefinition>()
const formatByMime = new Map<string, ViewerFormatDefinition>()

for (const definition of registry) {
  for (const extension of [definition.extension, ...definition.aliases]) {
    formatByExtension.set(extension, definition)
  }

  for (const mimeType of definition.mimeTypes) {
    formatByMime.set(mimeType, definition)
  }
}

export const viewerAcceptAttribute = registry
  .flatMap((definition) => [definition.extension, ...definition.aliases])
  .map((extension) => `.${extension}`)
  .join(',')

export function listViewerFormatsByFamily(family: ViewerFormatFamily): ViewerFormatDefinition[] {
  return registry.filter((definition) => definition.family === family)
}

export function normalizeExtension(rawValue: string): string {
  return rawValue.trim().toLowerCase().replace(/^\./u, '')
}

export function detectFileExtension(fileName: string): string {
  const parts = fileName.split('.')
  return parts.length > 1 ? normalizeExtension(parts[parts.length - 1] ?? '') : ''
}

export function resolveViewerFormat(
  fileName: string,
  mimeType?: string,
): ViewerFormatDefinition | null {
  const normalizedMimeType = mimeType?.trim().toLowerCase()
  if (normalizedMimeType && formatByMime.has(normalizedMimeType)) {
    return formatByMime.get(normalizedMimeType) ?? null
  }

  const extension = detectFileExtension(fileName)
  if (!extension) {
    return null
  }

  return formatByExtension.get(extension) ?? null
}
