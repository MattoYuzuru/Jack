export type ConverterFormatFamily = 'image' | 'document' | 'media'

export type ConverterSourceStrategyId =
  | 'native-raster'
  | 'heic-raster'
  | 'tiff-raster'
  | 'raw-raster'
  | 'psd-raster'
  | 'illustration-raster'

export type ConverterTargetStrategyId =
  | 'jpeg-encoder'
  | 'png-encoder'
  | 'webp-encoder'
  | 'pdf-document'
  | 'tiff-image'
  | 'avif-encoder'
  | 'svg-vectorizer'
  | 'ico-image'

export interface ConverterSourceFormatDefinition {
  extension: string
  aliases: string[]
  label: string
  family: ConverterFormatFamily
  mimeTypes: string[]
  sourceStrategyId: ConverterSourceStrategyId
  statusLabel: string
  notes: string
  accents: string[]
}

export interface ConverterTargetFormatDefinition {
  extension: string
  label: string
  family: ConverterFormatFamily
  mimeType: string
  targetStrategyId: ConverterTargetStrategyId
  supportsQuality: boolean
  supportsTransparency: boolean
  defaultQuality: number | null
  statusLabel: string
  notes: string
  accents: string[]
}

export interface ConverterScenarioDefinition {
  id: string
  family: ConverterFormatFamily
  label: string
  sourceExtension: string
  targetExtension: string
  statusLabel: string
  notes: string
  accents: string[]
}

const sourceFormatDefinitions: ConverterSourceFormatDefinition[] = [
  {
    extension: 'jpg',
    aliases: ['jpeg'],
    label: 'JPG',
    family: 'image',
    mimeTypes: ['image/jpeg'],
    sourceStrategyId: 'native-raster',
    statusLabel: 'Browser raster',
    notes: 'Нативный raster path для JPEG-family без промежуточного decode-слоя.',
    accents: ['Raster', 'Fast path'],
  },
  {
    extension: 'png',
    aliases: [],
    label: 'PNG',
    family: 'image',
    mimeTypes: ['image/png'],
    sourceStrategyId: 'native-raster',
    statusLabel: 'Browser raster',
    notes: 'Lossless raster с сохранением alpha-канала перед encode-шагом.',
    accents: ['Raster', 'Alpha'],
  },
  {
    extension: 'webp',
    aliases: [],
    label: 'WebP',
    family: 'image',
    mimeTypes: ['image/webp'],
    sourceStrategyId: 'native-raster',
    statusLabel: 'Browser raster',
    notes: 'Современный browser-supported raster source.',
    accents: ['Modern', 'Compressed'],
  },
  {
    extension: 'bmp',
    aliases: [],
    label: 'BMP',
    family: 'image',
    mimeTypes: ['image/bmp'],
    sourceStrategyId: 'native-raster',
    statusLabel: 'Browser raster',
    notes: 'Большие bitmap-файлы подготавливаются к более практичному target-формату.',
    accents: ['Bitmap', 'Legacy'],
  },
  {
    extension: 'svg',
    aliases: [],
    label: 'SVG',
    family: 'image',
    mimeTypes: ['image/svg+xml'],
    sourceStrategyId: 'native-raster',
    statusLabel: 'Browser raster',
    notes: 'Вектор рендерится в canvas и становится базой для raster-target.',
    accents: ['Vector', 'Rasterize'],
  },
  {
    extension: 'psd',
    aliases: [],
    label: 'PSD',
    family: 'image',
    mimeTypes: ['image/vnd.adobe.photoshop', 'application/vnd.adobe.photoshop'],
    sourceStrategyId: 'psd-raster',
    statusLabel: 'Server composite',
    notes:
      'Photoshop document уходит в backend IMAGE_CONVERT pipeline, где сводится к composite raster без browser-side PSD runtime.',
    accents: ['Adobe', 'Composite'],
  },
  {
    extension: 'ai',
    aliases: [],
    label: 'AI',
    family: 'image',
    mimeTypes: [],
    sourceStrategyId: 'illustration-raster',
    statusLabel: 'Server illustration',
    notes:
      'Illustrator source растеризуется в backend processing service через Ghostscript/ImageMagick path.',
    accents: ['Adobe', 'Illustration'],
  },
  {
    extension: 'eps',
    aliases: ['ps'],
    label: 'EPS',
    family: 'image',
    mimeTypes: [],
    sourceStrategyId: 'illustration-raster',
    statusLabel: 'Server illustration',
    notes:
      'Encapsulated PostScript растеризуется в backend processing service вместо browser-side preview extraction.',
    accents: ['PostScript', 'Preview'],
  },
  {
    extension: 'heic',
    aliases: ['heif'],
    label: 'HEIC',
    family: 'image',
    mimeTypes: ['image/heic', 'image/heif'],
    sourceStrategyId: 'heic-raster',
    statusLabel: 'Server rasterization',
    notes:
      'Apple image container больше не декодируется в браузере и проходит через backend IMAGE_CONVERT pipeline.',
    accents: ['Apple', 'Decode'],
  },
  {
    extension: 'tiff',
    aliases: ['tif'],
    label: 'TIFF',
    family: 'image',
    mimeTypes: ['image/tiff'],
    sourceStrategyId: 'tiff-raster',
    statusLabel: 'Server rasterization',
    notes:
      'TIFF-family уходит в backend IMAGE_CONVERT pipeline и получает preview/result artifacts вместо browser decode-layer.',
    accents: ['Archive', 'Decode'],
  },
  {
    extension: 'raw',
    aliases: ['dng', 'cr2', 'cr3', 'nef', 'arw', 'raf', 'rw2', 'orf', 'pef', 'srw'],
    label: 'RAW',
    family: 'image',
    mimeTypes: [],
    sourceStrategyId: 'raw-raster',
    statusLabel: 'Server rasterization',
    notes:
      'RAW-family забирает embedded preview на backend через libraw и больше не тянет браузерный TIFF-ish runtime.',
    accents: ['Camera', 'Preview'],
  },
]

const targetFormatDefinitions: ConverterTargetFormatDefinition[] = [
  {
    extension: 'jpg',
    label: 'JPG',
    family: 'image',
    mimeType: 'image/jpeg',
    targetStrategyId: 'jpeg-encoder',
    supportsQuality: true,
    supportsTransparency: false,
    defaultQuality: 0.9,
    statusLabel: 'Canvas encode',
    notes: 'Практичный raster-target для совместимости и веса, но без alpha-канала.',
    accents: ['Compatible', 'Opaque'],
  },
  {
    extension: 'png',
    label: 'PNG',
    family: 'image',
    mimeType: 'image/png',
    targetStrategyId: 'png-encoder',
    supportsQuality: false,
    supportsTransparency: true,
    defaultQuality: null,
    statusLabel: 'Canvas encode',
    notes: 'Lossless target с сохранением transparency.',
    accents: ['Lossless', 'Alpha'],
  },
  {
    extension: 'webp',
    label: 'WebP',
    family: 'image',
    mimeType: 'image/webp',
    targetStrategyId: 'webp-encoder',
    supportsQuality: true,
    supportsTransparency: true,
    defaultQuality: 0.9,
    statusLabel: 'Canvas encode',
    notes: 'Современный target для более компактного raster-выхода.',
    accents: ['Modern', 'Compact'],
  },
  {
    extension: 'avif',
    label: 'AVIF',
    family: 'image',
    mimeType: 'image/avif',
    targetStrategyId: 'avif-encoder',
    supportsQuality: true,
    supportsTransparency: true,
    defaultQuality: 0.78,
    statusLabel: 'Backend encode',
    notes:
      'Современный high-efficiency target теперь собирается через backend IMAGE_CONVERT и даёт PNG preview artifact для UI.',
    accents: ['Modern', 'WASM'],
  },
  {
    extension: 'svg',
    label: 'SVG',
    family: 'image',
    mimeType: 'image/svg+xml',
    targetStrategyId: 'svg-vectorizer',
    supportsQuality: false,
    supportsTransparency: true,
    defaultQuality: null,
    statusLabel: 'Backend trace',
    notes:
      'Векторный target собирается на backend через bitmap tracing и подходит для простых flat-график, а не для pixel-perfect roundtrip.',
    accents: ['Vector', 'Trace'],
  },
  {
    extension: 'ico',
    label: 'ICO',
    family: 'image',
    mimeType: 'image/x-icon',
    targetStrategyId: 'ico-image',
    supportsQuality: false,
    supportsTransparency: true,
    defaultQuality: null,
    statusLabel: 'Backend icon pack',
    notes:
      'Icon target собирается на backend в multi-size ICO container и держит PNG preview artifact для workspace.',
    accents: ['Icon', 'Multi-size'],
  },
  {
    extension: 'pdf',
    label: 'PDF',
    family: 'document',
    mimeType: 'application/pdf',
    targetStrategyId: 'pdf-document',
    supportsQuality: true,
    supportsTransparency: false,
    defaultQuality: 0.92,
    statusLabel: 'Backend document',
    notes:
      'Документный target: текущая итерация собирает single-page PDF на backend из подготовленного raster contract.',
    accents: ['Document', 'Single-page'],
  },
  {
    extension: 'tiff',
    label: 'TIFF',
    family: 'image',
    mimeType: 'image/tiff',
    targetStrategyId: 'tiff-image',
    supportsQuality: false,
    supportsTransparency: true,
    defaultQuality: null,
    statusLabel: 'Backend archive encode',
    notes:
      'Archive-friendly raster target теперь собирается на backend из unified raster contract.',
    accents: ['Archive', 'Lossless-ish'],
  },
]

const serverSourceExtensions = new Set(['heic', 'tiff', 'raw', 'psd', 'ai', 'eps'])
const serverTargetExtensions = new Set(['avif', 'svg', 'ico', 'tiff', 'pdf'])

const scenarioDefinitions: ConverterScenarioDefinition[] = [
  buildScenario('heic', 'jpg', 'HEIC decode -> JPG'),
  buildScenario('heic', 'avif', 'HEIC -> AVIF'),
  buildScenario('heic', 'tiff', 'HEIC -> TIFF'),
  buildScenario('png', 'jpg', 'PNG -> JPG'),
  buildScenario('png', 'webp', 'PNG -> WebP'),
  buildScenario('png', 'avif', 'PNG -> AVIF'),
  buildScenario('png', 'svg', 'PNG -> SVG trace'),
  buildScenario('png', 'ico', 'PNG -> ICO'),
  buildScenario('png', 'tiff', 'PNG -> TIFF'),
  buildScenario('jpg', 'png', 'JPG -> PNG'),
  buildScenario('jpg', 'webp', 'JPG -> WebP'),
  buildScenario('jpg', 'avif', 'JPG -> AVIF'),
  buildScenario('jpg', 'tiff', 'JPG -> TIFF'),
  buildScenario('webp', 'jpg', 'WebP -> JPG'),
  buildScenario('webp', 'png', 'WebP -> PNG'),
  buildScenario('webp', 'tiff', 'WebP -> TIFF'),
  buildScenario('bmp', 'jpg', 'BMP -> JPG'),
  buildScenario('bmp', 'png', 'BMP -> PNG'),
  buildScenario('bmp', 'tiff', 'BMP -> TIFF'),
  buildScenario('psd', 'jpg', 'PSD -> JPG'),
  buildScenario('psd', 'png', 'PSD -> PNG'),
  buildScenario('psd', 'webp', 'PSD -> WebP'),
  buildScenario('tiff', 'jpg', 'TIFF -> JPG'),
  buildScenario('tiff', 'pdf', 'TIFF -> PDF', 'document'),
  buildScenario('tiff', 'tiff', 'TIFF -> TIFF refresh'),
  buildScenario('raw', 'jpg', 'RAW -> JPG'),
  buildScenario('raw', 'pdf', 'RAW -> PDF', 'document'),
  buildScenario('raw', 'tiff', 'RAW -> TIFF'),
  buildScenario('jpg', 'pdf', 'JPG -> PDF', 'document'),
  buildScenario('png', 'pdf', 'PNG -> PDF', 'document'),
  buildScenario('webp', 'pdf', 'WebP -> PDF', 'document'),
  buildScenario('bmp', 'pdf', 'BMP -> PDF', 'document'),
  buildScenario('heic', 'pdf', 'HEIC -> PDF', 'document'),
  buildScenario('svg', 'png', 'SVG -> PNG'),
  buildScenario('svg', 'ico', 'SVG -> ICO'),
  buildScenario('svg', 'tiff', 'SVG -> TIFF'),
  buildScenario('svg', 'pdf', 'SVG -> PDF', 'document'),
  buildScenario('ai', 'png', 'AI -> PNG'),
  buildScenario('ai', 'pdf', 'AI -> PDF', 'document'),
  buildScenario('eps', 'png', 'EPS -> PNG'),
  buildScenario('eps', 'pdf', 'EPS -> PDF', 'document'),
]

const sourceByExtension = new Map<string, ConverterSourceFormatDefinition>()
const sourceByMimeType = new Map<string, ConverterSourceFormatDefinition>()
const targetByExtension = new Map<string, ConverterTargetFormatDefinition>()
const scenarioByPair = new Map<string, ConverterScenarioDefinition>()

for (const definition of sourceFormatDefinitions) {
  for (const extension of [definition.extension, ...definition.aliases]) {
    sourceByExtension.set(extension, definition)
  }

  for (const mimeType of definition.mimeTypes) {
    sourceByMimeType.set(mimeType, definition)
  }
}

for (const definition of targetFormatDefinitions) {
  targetByExtension.set(definition.extension, definition)
}

for (const scenario of scenarioDefinitions) {
  scenarioByPair.set(buildScenarioKey(scenario.sourceExtension, scenario.targetExtension), scenario)
}

export const converterAcceptAttribute = sourceFormatDefinitions
  .flatMap((definition) => [definition.extension, ...definition.aliases])
  .map((extension) => `.${extension}`)
  .join(',')

export function listConverterScenariosByFamily(
  family: ConverterFormatFamily,
): ConverterScenarioDefinition[] {
  return scenarioDefinitions.filter((scenario) => scenario.family === family)
}

export function normalizeConverterExtension(rawValue: string): string {
  return rawValue.trim().toLowerCase().replace(/^\./u, '')
}

export function detectConverterExtension(fileName: string): string {
  const parts = fileName.split('.')
  return parts.length > 1 ? normalizeConverterExtension(parts[parts.length - 1] ?? '') : ''
}

export function resolveConverterSourceFormat(
  fileName: string,
  mimeType?: string,
): ConverterSourceFormatDefinition | null {
  const normalizedMimeType = mimeType?.trim().toLowerCase()
  if (normalizedMimeType && sourceByMimeType.has(normalizedMimeType)) {
    return sourceByMimeType.get(normalizedMimeType) ?? null
  }

  const extension = detectConverterExtension(fileName)
  if (!extension) {
    return null
  }

  return sourceByExtension.get(extension) ?? null
}

export function resolveConverterTargetFormat(
  extension: string,
): ConverterTargetFormatDefinition | null {
  return targetByExtension.get(normalizeConverterExtension(extension)) ?? null
}

export function resolveConverterScenario(
  sourceExtension: string,
  targetExtension: string,
): ConverterScenarioDefinition | null {
  return (
    scenarioByPair.get(
      buildScenarioKey(
        normalizeConverterExtension(sourceExtension),
        normalizeConverterExtension(targetExtension),
      ),
    ) ?? null
  )
}

export function listConverterTargetsForSource(
  fileName: string,
  mimeType?: string,
): ConverterTargetFormatDefinition[] {
  const source = resolveConverterSourceFormat(fileName, mimeType)
  if (!source) {
    return []
  }

  return scenarioDefinitions
    .filter((scenario) => scenario.sourceExtension === source.extension)
    .map((scenario) => resolveConverterTargetFormat(scenario.targetExtension))
    .filter((target): target is ConverterTargetFormatDefinition => Boolean(target))
}

function buildScenario(
  sourceExtension: string,
  targetExtension: string,
  label: string,
  family: ConverterFormatFamily = 'image',
): ConverterScenarioDefinition {
  const isServerScenario =
    serverSourceExtensions.has(sourceExtension) || serverTargetExtensions.has(targetExtension)

  return {
    id: buildScenarioKey(sourceExtension, targetExtension),
    family,
    label,
    sourceExtension,
    targetExtension,
    statusLabel: isServerScenario ? 'Server-assisted' : 'Browser-native',
    notes: isServerScenario
      ? 'Тяжёлый сценарий идёт через backend IMAGE_CONVERT jobs: frontend остаётся orchestration/UI слоем и получает preview/result artifacts.'
      : 'Быстрый сценарий закрывается прямо в клиенте через browser-native raster pipeline без backend round-trip.',
    accents: [sourceExtension.toUpperCase(), targetExtension.toUpperCase()],
  }
}

function buildScenarioKey(sourceExtension: string, targetExtension: string): string {
  return `${sourceExtension}->${targetExtension}`
}
