export type ViewerFormatFamily = 'image' | 'document' | 'media' | 'data'

export type ViewerPreviewPipeline = 'browser-native' | 'client-decode' | 'planned'

export type PreviewStrategyId = 'native-image' | 'heic-image' | 'tiff-image' | 'raw-image'

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

const registry = [...imageFormatDefinitions]

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
