export type ConverterFormatFamily = 'image' | 'document' | 'media'

export type ConverterSourceStrategyId =
  | 'native-raster'
  | 'heic-raster'
  | 'tiff-raster'
  | 'raw-raster'

export type ConverterTargetStrategyId =
  | 'jpeg-encoder'
  | 'png-encoder'
  | 'webp-encoder'
  | 'pdf-document'

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
    extension: 'heic',
    aliases: ['heif'],
    label: 'HEIC',
    family: 'image',
    mimeTypes: ['image/heic', 'image/heif'],
    sourceStrategyId: 'heic-raster',
    statusLabel: 'Decode adapter',
    notes: 'Apple image container проходит через отдельный decode-layer перед encode.',
    accents: ['Apple', 'Decode'],
  },
  {
    extension: 'tiff',
    aliases: ['tif'],
    label: 'TIFF',
    family: 'image',
    mimeTypes: ['image/tiff'],
    sourceStrategyId: 'tiff-raster',
    statusLabel: 'Decode adapter',
    notes: 'TIFF-family распаковывается в renderable raster через UTIF pipeline.',
    accents: ['Archive', 'Decode'],
  },
  {
    extension: 'raw',
    aliases: ['dng', 'cr2', 'cr3', 'nef', 'arw', 'raf', 'rw2', 'orf', 'pef', 'srw'],
    label: 'RAW',
    family: 'image',
    mimeTypes: [],
    sourceStrategyId: 'raw-raster',
    statusLabel: 'Preview extraction',
    notes: 'RAW-family забирает embedded preview и передаёт его дальше как единый raster contract.',
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
    extension: 'pdf',
    label: 'PDF',
    family: 'document',
    mimeType: 'application/pdf',
    targetStrategyId: 'pdf-document',
    supportsQuality: true,
    supportsTransparency: false,
    defaultQuality: 0.92,
    statusLabel: 'Single-page document',
    notes:
      'Документный target: текущая итерация собирает single-page PDF из подготовленного raster contract.',
    accents: ['Document', 'Single-page'],
  },
]

const scenarioDefinitions: ConverterScenarioDefinition[] = [
  buildScenario('heic', 'jpg', 'HEIC decode -> JPG'),
  buildScenario('png', 'jpg', 'PNG -> JPG'),
  buildScenario('jpg', 'png', 'JPG -> PNG'),
  buildScenario('jpg', 'webp', 'JPG -> WebP'),
  buildScenario('png', 'webp', 'PNG -> WebP'),
  buildScenario('webp', 'jpg', 'WebP -> JPG'),
  buildScenario('webp', 'png', 'WebP -> PNG'),
  buildScenario('bmp', 'jpg', 'BMP -> JPG'),
  buildScenario('bmp', 'png', 'BMP -> PNG'),
  buildScenario('tiff', 'jpg', 'TIFF -> JPG'),
  buildScenario('tiff', 'pdf', 'TIFF -> PDF', 'document'),
  buildScenario('raw', 'jpg', 'RAW -> JPG'),
  buildScenario('raw', 'pdf', 'RAW -> PDF', 'document'),
  buildScenario('jpg', 'pdf', 'JPG -> PDF', 'document'),
  buildScenario('png', 'pdf', 'PNG -> PDF', 'document'),
  buildScenario('webp', 'pdf', 'WebP -> PDF', 'document'),
  buildScenario('bmp', 'pdf', 'BMP -> PDF', 'document'),
  buildScenario('heic', 'pdf', 'HEIC -> PDF', 'document'),
  buildScenario('svg', 'png', 'SVG -> PNG'),
  buildScenario('svg', 'pdf', 'SVG -> PDF', 'document'),
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
  return {
    id: buildScenarioKey(sourceExtension, targetExtension),
    family,
    label,
    sourceExtension,
    targetExtension,
    statusLabel: 'Browser-first',
    notes:
      'Сценарий закрывается в клиенте через decode/encode pipeline и готов к расширению новыми source/target стратегиями.',
    accents: [sourceExtension.toUpperCase(), targetExtension.toUpperCase()],
  }
}

function buildScenarioKey(sourceExtension: string, targetExtension: string): string {
  return `${sourceExtension}->${targetExtension}`
}
