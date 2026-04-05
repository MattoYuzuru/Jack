export type ViewerFormatFamily = 'image' | 'document' | 'media' | 'audio' | 'data'

export type ViewerPreviewPipeline =
  | 'browser-native'
  | 'client-decode'
  | 'server-assisted'
  | 'planned'

export type PreviewStrategyId =
  | 'native-image'
  | 'heic-image'
  | 'tiff-image'
  | 'raw-image'
  | 'native-video'
  | 'legacy-video'
  | 'native-audio'
  | 'legacy-audio'
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
    previewPipeline: 'server-assisted',
    previewStrategyId: 'heic-image',
    statusLabel: 'Server image preview',
    notes:
      'HEIC preview больше не декодируется в браузере и идёт через backend IMAGE_CONVERT preview artifact.',
    accents: ['Apple', 'Decode'],
  },
  {
    extension: 'tiff',
    aliases: ['tif'],
    label: 'TIFF',
    family: 'image',
    mimeTypes: ['image/tiff'],
    previewPipeline: 'server-assisted',
    previewStrategyId: 'tiff-image',
    statusLabel: 'Server image preview',
    notes:
      'Многостраничные и сжатые TIFF теперь собирают browser-friendly preview через backend IMAGE_CONVERT.',
    accents: ['Archive', 'Decode'],
  },
  {
    extension: 'raw',
    aliases: ['dng', 'cr2', 'cr3', 'nef', 'arw', 'raf', 'rw2', 'orf', 'pef', 'srw'],
    label: 'RAW',
    family: 'image',
    mimeTypes: [],
    previewPipeline: 'server-assisted',
    previewStrategyId: 'raw-image',
    statusLabel: 'Server image preview',
    notes:
      'RAW family забирает embedded preview на backend и даёт тот же image workspace без browser-heavy decode.',
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
    previewPipeline: 'server-assisted',
    previewStrategyId: 'pdf-document',
    statusLabel: 'Server document preview',
    notes:
      'PDF preview теперь подготавливается через backend DOCUMENT_PREVIEW: backend собирает page stats и search layer, а браузер получает готовый PDF artifact для embed.',
    accents: ['Pages', 'Search layer'],
  },
  {
    extension: 'txt',
    aliases: [],
    label: 'TXT',
    family: 'document',
    mimeTypes: ['text/plain'],
    previewPipeline: 'server-assisted',
    previewStrategyId: 'text-document',
    statusLabel: 'Server text preview',
    notes:
      'Plain text проходит через backend DOCUMENT_PREVIEW и возвращается в общий text/search contract без локального decode runtime.',
    accents: ['Text', 'Search'],
  },
  {
    extension: 'csv',
    aliases: [],
    label: 'CSV',
    family: 'document',
    mimeTypes: ['text/csv', 'application/csv'],
    previewPipeline: 'server-assisted',
    previewStrategyId: 'csv-document',
    statusLabel: 'Server table preview',
    notes:
      'CSV preview теперь собирается backend-side: браузер получает bounded table payload, delimiter summary и searchable text layer.',
    accents: ['Table', 'Search'],
  },
  {
    extension: 'html',
    aliases: ['htm'],
    label: 'HTML',
    family: 'document',
    mimeTypes: ['text/html'],
    previewPipeline: 'server-assisted',
    previewStrategyId: 'html-document',
    statusLabel: 'Server sandbox preview',
    notes:
      'HTML сначала проходит через backend sanitization и outline extraction, а затем рендерится в sandbox iframe как готовый srcdoc.',
    accents: ['Sandbox', 'Outline'],
  },
  {
    extension: 'rtf',
    aliases: [],
    label: 'RTF',
    family: 'document',
    mimeTypes: ['application/rtf', 'text/rtf'],
    previewPipeline: 'server-assisted',
    previewStrategyId: 'rtf-document',
    statusLabel: 'Server text extraction',
    notes:
      'RTF теперь извлекается на backend и сводится к text/search layer без faithful layout render в браузере.',
    accents: ['Legacy', 'Search'],
  },
  {
    extension: 'doc',
    aliases: [],
    label: 'DOC',
    family: 'document',
    mimeTypes: ['application/msword'],
    previewPipeline: 'server-assisted',
    previewStrategyId: 'doc-document',
    statusLabel: 'Server legacy adapter',
    notes:
      'Legacy Word теперь разбирается backend-side: viewer получает readable text layer и search без локального binary parser.',
    accents: ['Word', 'Legacy'],
  },
  {
    extension: 'docx',
    aliases: [],
    label: 'DOCX',
    family: 'document',
    mimeTypes: ['application/vnd.openxmlformats-officedocument.wordprocessingml.document'],
    previewPipeline: 'server-assisted',
    previewStrategyId: 'docx-document',
    statusLabel: 'Server OOXML preview',
    notes:
      'DOCX проходит через backend OOXML parser: headings, tables, текстовый слой и document HTML preview приходят готовым payload.',
    accents: ['Word', 'OOXML'],
  },
  {
    extension: 'odt',
    aliases: [],
    label: 'ODT',
    family: 'document',
    mimeTypes: ['application/vnd.oasis.opendocument.text'],
    previewPipeline: 'server-assisted',
    previewStrategyId: 'odt-document',
    statusLabel: 'Server archive adapter',
    notes:
      'ODT теперь разбирается на backend как zip/xml документ и возвращается как headings/tables/searchable text payload.',
    accents: ['OpenDocument', 'Archive'],
  },
  {
    extension: 'xls',
    aliases: [],
    label: 'XLS',
    family: 'document',
    mimeTypes: ['application/vnd.ms-excel'],
    previewPipeline: 'server-assisted',
    previewStrategyId: 'xls-document',
    statusLabel: 'Server workbook preview',
    notes:
      'Legacy Excel теперь проходит через backend workbook adapter: viewer получает sheet/table contract без локального workbook decode.',
    accents: ['Spreadsheet', 'Legacy'],
  },
  {
    extension: 'xlsx',
    aliases: [],
    label: 'XLSX',
    family: 'document',
    mimeTypes: ['application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'],
    previewPipeline: 'server-assisted',
    previewStrategyId: 'xlsx-document',
    statusLabel: 'Server workbook preview',
    notes:
      'XLSX поднимается через backend workbook preview: sheets, табличная проекция и search layer приходят как готовый payload.',
    accents: ['Spreadsheet', 'OOXML'],
  },
  {
    extension: 'pptx',
    aliases: [],
    label: 'PPTX',
    family: 'document',
    mimeTypes: ['application/vnd.openxmlformats-officedocument.presentationml.presentation'],
    previewPipeline: 'server-assisted',
    previewStrategyId: 'pptx-document',
    statusLabel: 'Server slide preview',
    notes:
      'PPTX идёт через backend slide-aware parser: viewer получает deck summary, slide titles и searchable text layer без локального OOXML runtime.',
    accents: ['Slides', 'OOXML'],
  },
  {
    extension: 'epub',
    aliases: [],
    label: 'EPUB',
    family: 'document',
    mimeTypes: ['application/epub+zip'],
    previewPipeline: 'server-assisted',
    previewStrategyId: 'epub-document',
    statusLabel: 'Server reading preview',
    notes:
      'EPUB собирается backend-side как reflowable reading layer: outline и searchable text приходят без локального archive parser.',
    accents: ['Book', 'Reflow'],
  },
  {
    extension: 'db',
    aliases: [],
    label: 'DB',
    family: 'document',
    mimeTypes: [],
    previewPipeline: 'server-assisted',
    previewStrategyId: 'sqlite-document',
    statusLabel: 'Server SQLite introspection',
    notes:
      'DB route теперь предполагает SQLite-compatible container и разбирается через backend DOCUMENT_PREVIEW read-only introspection path.',
    accents: ['Data', 'Schema'],
  },
  {
    extension: 'sqlite',
    aliases: [],
    label: 'SQLite',
    family: 'document',
    mimeTypes: ['application/vnd.sqlite3'],
    previewPipeline: 'server-assisted',
    previewStrategyId: 'sqlite-document',
    statusLabel: 'Server SQLite introspection',
    notes:
      'SQLite viewer теперь получает schema-aware preview с backend: таблицы, sample rows, create SQL и searchable text остаются в read-only contract.',
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
    previewPipeline: 'server-assisted',
    previewStrategyId: 'legacy-video',
    statusLabel: 'Server media preview',
    notes:
      'AVI идёт через backend MEDIA_PREVIEW job: сервер собирает browser-friendly preview artifact и возвращает его в тот же video workspace contract.',
    accents: ['Video', 'Legacy'],
  },
  {
    extension: 'mkv',
    aliases: [],
    label: 'MKV',
    family: 'media',
    mimeTypes: ['video/x-matroska'],
    previewPipeline: 'server-assisted',
    previewStrategyId: 'legacy-video',
    statusLabel: 'Server media preview',
    notes:
      'Matroska идёт через backend media adapter: контейнер нормализуется server-side и затем воспроизводится в том же browser-native player path.',
    accents: ['Video', 'Container'],
  },
  {
    extension: 'wmv',
    aliases: [],
    label: 'WMV',
    family: 'media',
    mimeTypes: ['video/x-ms-wmv'],
    previewPipeline: 'server-assisted',
    previewStrategyId: 'legacy-video',
    statusLabel: 'Server media preview',
    notes:
      'WMV проходит через backend compatibility bridge и получает browser-oriented preview, чтобы workspace не зависел от platform codec support.',
    accents: ['Video', 'Windows'],
  },
  {
    extension: 'flv',
    aliases: [],
    label: 'FLV',
    family: 'media',
    mimeTypes: ['video/x-flv'],
    previewPipeline: 'server-assisted',
    previewStrategyId: 'legacy-video',
    statusLabel: 'Server media preview',
    notes:
      'FLV идёт через backend transcode bridge, потому что Flash-era контейнер сам по себе не подходит для современного browser playback path.',
    accents: ['Video', 'Archive'],
  },
]

const audioFormatDefinitions: ViewerFormatDefinition[] = [
  {
    extension: 'mp3',
    aliases: [],
    label: 'MP3',
    family: 'audio',
    mimeTypes: ['audio/mpeg'],
    previewPipeline: 'browser-native',
    previewStrategyId: 'native-audio',
    statusLabel: 'Browser audio',
    notes:
      'Основной audio fast path: MP3 открывается напрямую в browser-native audio element и получает waveform, tag inspector и playback tooling.',
    accents: ['Audio', 'Native'],
  },
  {
    extension: 'wav',
    aliases: [],
    label: 'WAV',
    family: 'audio',
    mimeTypes: ['audio/wav', 'audio/wave', 'audio/x-wav'],
    previewPipeline: 'browser-native',
    previewStrategyId: 'native-audio',
    statusLabel: 'Browser audio',
    notes:
      'WAV идёт через нативный playback path и удобен для точной проверки длительности, формы сигнала и базовых technical metadata.',
    accents: ['Audio', 'Lossless'],
  },
  {
    extension: 'ogg',
    aliases: [],
    label: 'OGG',
    family: 'audio',
    mimeTypes: ['audio/ogg'],
    previewPipeline: 'browser-native',
    previewStrategyId: 'native-audio',
    statusLabel: 'Browser audio',
    notes:
      'OGG-контейнер воспроизводится напрямую и получает тот же audio workspace без отдельной ветки UI.',
    accents: ['Audio', 'Open'],
  },
  {
    extension: 'opus',
    aliases: [],
    label: 'OPUS',
    family: 'audio',
    mimeTypes: ['audio/opus', 'audio/ogg; codecs=opus'],
    previewPipeline: 'browser-native',
    previewStrategyId: 'native-audio',
    statusLabel: 'Browser audio',
    notes:
      'OPUS использует browser-native path там, где контейнер уже совместим с audio element и Web Audio decode pipeline.',
    accents: ['Audio', 'Speech'],
  },
  {
    extension: 'aac',
    aliases: [],
    label: 'AAC',
    family: 'audio',
    mimeTypes: ['audio/aac'],
    previewPipeline: 'server-assisted',
    previewStrategyId: 'legacy-audio',
    statusLabel: 'Server audio preview',
    notes:
      'AAC закрывается через backend MEDIA_PREVIEW job, чтобы viewer не зависел от platform-specific поддержки контейнера и профиля кодека.',
    accents: ['Audio', 'Bridge'],
  },
  {
    extension: 'flac',
    aliases: [],
    label: 'FLAC',
    family: 'audio',
    mimeTypes: ['audio/flac', 'audio/x-flac'],
    previewPipeline: 'server-assisted',
    previewStrategyId: 'legacy-audio',
    statusLabel: 'Server audio preview',
    notes:
      'FLAC проходит через backend compatibility bridge и получает browser-friendly playback artifact вместе с waveform и metadata inspector.',
    accents: ['Audio', 'Lossless'],
  },
  {
    extension: 'aiff',
    aliases: ['aif'],
    label: 'AIFF',
    family: 'audio',
    mimeTypes: ['audio/aiff', 'audio/x-aiff'],
    previewPipeline: 'server-assisted',
    previewStrategyId: 'legacy-audio',
    statusLabel: 'Server audio preview',
    notes:
      'AIFF идёт через backend transcode bridge: контейнер нормализуется в browser-playable runtime path без отдельного renderer.',
    accents: ['Audio', 'Archive'],
  },
]

const registry = [
  ...imageFormatDefinitions,
  ...documentFormatDefinitions,
  ...mediaFormatDefinitions,
  ...audioFormatDefinitions,
]

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
