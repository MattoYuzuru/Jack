export type ViewerFormatFamily = 'image' | 'document' | 'media' | 'data'

export type ViewerPreviewPipeline = 'browser-native' | 'server-pipeline' | 'planned'

export type PreviewStrategyId = 'native-image' | 'deferred'

export interface ViewerFormatDefinition {
  extension: string
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
    label: 'AVIF',
    family: 'image',
    mimeTypes: ['image/avif'],
    previewPipeline: 'browser-native',
    previewStrategyId: 'native-image',
    statusLabel: 'Browser preview',
    notes: 'В современных браузерах открывается напрямую, без backend pipeline.',
    accents: ['Modern', 'High efficiency'],
  },
  {
    extension: 'gif',
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
    label: 'HEIC',
    family: 'image',
    mimeTypes: ['image/heic', 'image/heif'],
    previewPipeline: 'server-pipeline',
    previewStrategyId: 'deferred',
    statusLabel: 'Pipeline required',
    notes: 'Для надёжного preview нужен серверный decode и нормализация в web-friendly представление.',
    accents: ['Apple', 'Decode'],
  },
  {
    extension: 'tiff',
    label: 'TIFF',
    family: 'image',
    mimeTypes: ['image/tiff'],
    previewPipeline: 'server-pipeline',
    previewStrategyId: 'deferred',
    statusLabel: 'Pipeline required',
    notes: 'Многостраничность и вариативность кодеков лучше закрывать через backend adapter.',
    accents: ['Archive', 'Multi-page'],
  },
  {
    extension: 'raw',
    label: 'RAW',
    family: 'image',
    mimeTypes: [],
    previewPipeline: 'server-pipeline',
    previewStrategyId: 'deferred',
    statusLabel: 'Pipeline required',
    notes: 'Для RAW понадобится семейство адаптеров под конкретные камеры и контейнеры.',
    accents: ['Camera', 'Decode'],
  },
]

const registry = [...imageFormatDefinitions]

const formatByExtension = new Map(registry.map((definition) => [definition.extension, definition]))
const formatByMime = new Map(
  registry.flatMap((definition) => definition.mimeTypes.map((mimeType) => [mimeType, definition] as const)),
)

export const viewerAcceptAttribute = registry.map((definition) => `.${definition.extension}`).join(',')

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
