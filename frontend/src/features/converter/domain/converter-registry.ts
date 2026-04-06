import { getProcessingCapabilityScope } from '../../processing/application/processing-client'

export type ConverterFormatFamily = 'image' | 'document' | 'media'
export type ConverterScenarioExecutionMode = 'browser-native' | 'server-assisted'

export type ConverterSourceStrategyId =
  | 'native-raster'
  | 'heic-raster'
  | 'tiff-raster'
  | 'raw-raster'
  | 'psd-raster'
  | 'illustration-raster'
  | 'pdf-document'
  | 'office-document'
  | 'spreadsheet-document'
  | 'presentation-document'
  | 'video-media'
  | 'audio-media'

export type ConverterTargetStrategyId =
  | 'jpeg-encoder'
  | 'png-encoder'
  | 'webp-encoder'
  | 'pdf-document'
  | 'tiff-image'
  | 'avif-encoder'
  | 'svg-vectorizer'
  | 'ico-image'
  | 'docx-document'
  | 'txt-document'
  | 'html-document'
  | 'rtf-document'
  | 'odt-document'
  | 'xlsx-document'
  | 'csv-document'
  | 'ods-document'
  | 'pptx-document'
  | 'mp4-video'
  | 'webm-video'
  | 'gif-image'
  | 'mp3-audio'
  | 'wav-audio'
  | 'aac-audio'
  | 'm4a-audio'
  | 'flac-audio'

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
  available: boolean
  availabilityDetail: string | null
  requiredJobTypes: string[]
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
  available: boolean
  availabilityDetail: string | null
  requiredJobTypes: string[]
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
  executionMode: ConverterScenarioExecutionMode
  available: boolean
  availabilityDetail: string | null
  requiredJobTypes: string[]
}

export interface ConverterCapabilityMatrix {
  acceptAttribute: string
  sourceFormats: ConverterSourceFormatDefinition[]
  targetFormats: ConverterTargetFormatDefinition[]
  scenarios: ConverterScenarioDefinition[]
  presets: unknown[]
}

export async function getConverterCapabilityMatrix(): Promise<ConverterCapabilityMatrix> {
  const scope = await getProcessingCapabilityScope('converter')
  const matrix = scope.converterMatrix as ConverterCapabilityMatrix | null | undefined

  if (!matrix) {
    throw new Error('Не удалось загрузить доступные направления конвертации.')
  }

  return {
    acceptAttribute: matrix.acceptAttribute || '',
    sourceFormats: Array.isArray(matrix.sourceFormats) ? matrix.sourceFormats : [],
    targetFormats: Array.isArray(matrix.targetFormats) ? matrix.targetFormats : [],
    scenarios: Array.isArray(matrix.scenarios) ? matrix.scenarios : [],
    presets: Array.isArray(matrix.presets) ? matrix.presets : [],
  }
}

export async function getConverterAcceptAttribute(): Promise<string> {
  const matrix = await getConverterCapabilityMatrix()
  return matrix.acceptAttribute
}

export async function listConverterScenariosByFamily(
  family: ConverterFormatFamily,
): Promise<ConverterScenarioDefinition[]> {
  const matrix = await getConverterCapabilityMatrix()
  return matrix.scenarios.filter((scenario) => scenario.family === family)
}

export function normalizeConverterExtension(rawValue: string): string {
  return rawValue.trim().toLowerCase().replace(/^\./u, '')
}

export function detectConverterExtension(fileName: string): string {
  const parts = fileName.split('.')
  return parts.length > 1 ? normalizeConverterExtension(parts[parts.length - 1] ?? '') : ''
}

export async function resolveConverterSourceFormat(
  fileName: string,
  mimeType?: string,
): Promise<ConverterSourceFormatDefinition | null> {
  const matrix = await getConverterCapabilityMatrix()
  const normalizedMimeType = mimeType?.trim().toLowerCase()

  if (normalizedMimeType) {
    const matchByMime = matrix.sourceFormats.find((definition) =>
      definition.mimeTypes.some(
        (candidate) => candidate.trim().toLowerCase() === normalizedMimeType,
      ),
    )

    if (matchByMime) {
      return matchByMime
    }
  }

  const extension = detectConverterExtension(fileName)
  if (!extension) {
    return null
  }

  return (
    matrix.sourceFormats.find(
      (definition) => definition.extension === extension || definition.aliases.includes(extension),
    ) ?? null
  )
}

export async function resolveConverterTargetFormat(
  extension: string,
): Promise<ConverterTargetFormatDefinition | null> {
  const matrix = await getConverterCapabilityMatrix()
  const normalizedExtension = normalizeConverterExtension(extension)

  return (
    matrix.targetFormats.find((definition) => definition.extension === normalizedExtension) ?? null
  )
}

export async function resolveConverterScenario(
  sourceExtension: string,
  targetExtension: string,
): Promise<ConverterScenarioDefinition | null> {
  const matrix = await getConverterCapabilityMatrix()
  const normalizedSource = normalizeConverterExtension(sourceExtension)
  const normalizedTarget = normalizeConverterExtension(targetExtension)

  return (
    matrix.scenarios.find(
      (scenario) =>
        scenario.sourceExtension === normalizedSource &&
        scenario.targetExtension === normalizedTarget,
    ) ?? null
  )
}

export async function listConverterTargetsForSource(
  fileName: string,
  mimeType?: string,
): Promise<ConverterTargetFormatDefinition[]> {
  const [matrix, source] = await Promise.all([
    getConverterCapabilityMatrix(),
    resolveConverterSourceFormat(fileName, mimeType),
  ])
  if (!source) {
    return []
  }

  return matrix.scenarios
    .filter((scenario) => scenario.sourceExtension === source.extension && scenario.available)
    .flatMap((scenario) => {
      const target =
        matrix.targetFormats.find(
          (candidate) => candidate.extension === scenario.targetExtension,
        ) ?? null

      return target && target.available ? [target] : []
    })
}
