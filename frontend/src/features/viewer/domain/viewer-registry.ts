export type ViewerFormatFamily = 'image' | 'document' | 'media' | 'data'

export type ViewerPreviewPipeline = 'browser-native' | 'client-decode' | 'planned'

export type PreviewStrategyId =
  | 'native-image'
  | 'heic-image'
  | 'tiff-image'
  | 'raw-image'
  | 'native-video'
  | 'pdf-document'
  | 'text-document'
  | 'csv-document'
  | 'html-document'
  | 'rtf-document'
  | 'doc-document'
  | 'docx-document'
  | 'odt-document'
  | 'xls-document'
  | 'xlsx-document'
  | 'pptx-document'
  | 'epub-document'
  | 'sqlite-document'
  | 'planned-media'

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
    previewPipeline: 'client-decode',
    previewStrategyId: 'doc-document',
    statusLabel: 'Legacy adapter',
    notes:
      'Legacy Word идёт через binary text extraction adapter: viewer поднимает readable text layer и search, но не обещает faithful layout Word.',
    accents: ['Word', 'Legacy'],
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
    previewPipeline: 'client-decode',
    previewStrategyId: 'odt-document',
    statusLabel: 'Archive adapter',
    notes:
      'ODT разбирается как zip/xml документ: viewer поднимает headings, таблицы и searchable text поверх общего HTML-oriented document layer.',
    accents: ['OpenDocument', 'Archive'],
  },
  {
    extension: 'xls',
    aliases: [],
    label: 'XLS',
    family: 'document',
    mimeTypes: ['application/vnd.ms-excel'],
    previewPipeline: 'client-decode',
    previewStrategyId: 'xls-document',
    statusLabel: 'Legacy workbook',
    notes:
      'Legacy Excel проходит через workbook decode path: viewer нормализует листы и табличные данные в тот же contract, что и XLSX.',
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
    previewPipeline: 'client-decode',
    previewStrategyId: 'epub-document',
    statusLabel: 'Reading adapter',
    notes:
      'EPUB собирается как reflowable reading layer со spine-aware chapter parsing, outline и searchable text без faithful e-book chrome.',
    accents: ['Book', 'Reflow'],
  },
  {
    extension: 'db',
    aliases: [],
    label: 'DB',
    family: 'document',
    mimeTypes: [],
    previewPipeline: 'client-decode',
    previewStrategyId: 'sqlite-document',
    statusLabel: 'SQLite introspection',
    notes:
      'DB route сейчас предполагает SQLite-compatible container: viewer поднимает schema/table preview, а для не-SQLite сигнатур возвращает честный fallback.',
    accents: ['Data', 'Schema'],
  },
  {
    extension: 'sqlite',
    aliases: [],
    label: 'SQLite',
    family: 'document',
    mimeTypes: ['application/vnd.sqlite3'],
    previewPipeline: 'client-decode',
    previewStrategyId: 'sqlite-document',
    statusLabel: 'SQLite introspection',
    notes:
      'SQLite viewer поднимает schema-aware preview: список таблиц, sample rows, create SQL и searchable text поверх read-only introspection слоя.',
    accents: ['Data', 'Database'],
  },
]

const mediaFormatDefinitions: ViewerFormatDefinition[] = [
  {
    extension: 'mp4',
    aliases: [],
    label: 'MP4',
    family: 'media',
    mimeTypes: ['video/mp4'],
    previewPipeline: 'browser-native',
    previewStrategyId: 'native-video',
    statusLabel: 'Browser video',
    notes:
      'Основной video fast path: MP4 идёт через browser-native player с metadata inspection, scrubbing и playback controls внутри viewer workspace.',
    accents: ['Video', 'Native'],
  },
  {
    extension: 'mov',
    aliases: [],
    label: 'MOV',
    family: 'media',
    mimeTypes: ['video/quicktime'],
    previewPipeline: 'browser-native',
    previewStrategyId: 'native-video',
    statusLabel: 'Codec dependent',
    notes:
      'MOV использует тот же browser-native player, но реальная воспроизводимость зависит от конкретного codec stack в браузере.',
    accents: ['Video', 'Codec dependent'],
  },
  {
    extension: 'webm',
    aliases: [],
    label: 'WebM',
    family: 'media',
    mimeTypes: ['video/webm'],
    previewPipeline: 'browser-native',
    previewStrategyId: 'native-video',
    statusLabel: 'Browser video',
    notes:
      'WebM хорошо ложится в browser-native video path и получает тот же player UX без отдельного decode-layer.',
    accents: ['Video', 'Web native'],
  },
  {
    extension: 'avi',
    aliases: [],
    label: 'AVI',
    family: 'media',
    mimeTypes: ['video/x-msvideo'],
    previewPipeline: 'planned',
    previewStrategyId: 'planned-media',
    statusLabel: 'Foundation only',
    notes:
      'AVI распознан в capability map, но для стабильного preview ему позже понадобится decode/transcode bridge поверх browser player.',
    accents: ['Video', 'Legacy'],
  },
  {
    extension: 'mkv',
    aliases: [],
    label: 'MKV',
    family: 'media',
    mimeTypes: ['video/x-matroska'],
    previewPipeline: 'planned',
    previewStrategyId: 'planned-media',
    statusLabel: 'Foundation only',
    notes:
      'Matroska-контейнер заведён в registry, но без отдельного playback adapter browser support остаётся слишком нестабильным.',
    accents: ['Video', 'Container'],
  },
  {
    extension: 'wmv',
    aliases: [],
    label: 'WMV',
    family: 'media',
    mimeTypes: ['video/x-ms-wmv'],
    previewPipeline: 'planned',
    previewStrategyId: 'planned-media',
    statusLabel: 'Foundation only',
    notes:
      'WMV позже потребует отдельный compatibility/decode path, чтобы не зависеть от старого platform codec support.',
    accents: ['Video', 'Windows'],
  },
  {
    extension: 'flv',
    aliases: [],
    label: 'FLV',
    family: 'media',
    mimeTypes: ['video/x-flv'],
    previewPipeline: 'planned',
    previewStrategyId: 'planned-media',
    statusLabel: 'Foundation only',
    notes:
      'FLV заведён как будущий legacy adapter scenario: foundation есть, но browser-native playback для него не обещается.',
    accents: ['Video', 'Legacy'],
  },
]

const registry = [...imageFormatDefinitions, ...documentFormatDefinitions, ...mediaFormatDefinitions]

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
