import { getProcessingCapabilityScope } from '../../processing/application/processing-client'

export type CompressionFormatFamily = 'image' | 'media' | 'audio'

export interface CompressionSourceFormatDefinition {
  extension: string
  aliases: string[]
  label: string
  family: CompressionFormatFamily
  mimeTypes: string[]
  targetExtensions: string[]
  defaultTargetExtension: string | null
  statusLabel: string
  notes: string
  accents: string[]
  available: boolean
  availabilityDetail: string | null
  requiredJobTypes: string[]
}

export interface CompressionTargetFormatDefinition {
  extension: string
  label: string
  family: CompressionFormatFamily
  supportsQuality: boolean
  supportsTransparency: boolean
  supportsResolutionLimits: boolean
  supportsBitrateControls: boolean
  supportsFpsControl: boolean
  defaultQuality: number | null
  statusLabel: string
  notes: string
  accents: string[]
  available: boolean
  availabilityDetail: string | null
  requiredJobTypes: string[]
}

export interface CompressionModeDefinition {
  id: 'maximum' | 'target-size' | 'custom'
  label: string
  detail: string
  accents: string[]
  requiresTargetSize: boolean
  supportsTargetSelection: boolean
  supportsCustomSettings: boolean
}

export interface CompressionCapabilityMatrix {
  acceptAttribute: string
  sourceFormats: CompressionSourceFormatDefinition[]
  targetFormats: CompressionTargetFormatDefinition[]
  modes: CompressionModeDefinition[]
}

export async function getCompressionCapabilityMatrix(): Promise<CompressionCapabilityMatrix> {
  const scope = await getProcessingCapabilityScope('compression')
  const matrix = scope.compressionMatrix as CompressionCapabilityMatrix | null | undefined

  if (!matrix) {
    throw new Error('Не удалось загрузить доступные режимы сжатия.')
  }

  return {
    acceptAttribute: matrix.acceptAttribute || '',
    sourceFormats: Array.isArray(matrix.sourceFormats) ? matrix.sourceFormats : [],
    targetFormats: Array.isArray(matrix.targetFormats) ? matrix.targetFormats : [],
    modes: Array.isArray(matrix.modes) ? matrix.modes : [],
  }
}

export async function getCompressionAcceptAttribute(): Promise<string> {
  const matrix = await getCompressionCapabilityMatrix()
  return matrix.acceptAttribute
}

export async function getCompressionModes(): Promise<CompressionModeDefinition[]> {
  const matrix = await getCompressionCapabilityMatrix()
  return matrix.modes
}

export function normalizeCompressionExtension(rawValue: string): string {
  return rawValue.trim().toLowerCase().replace(/^\./u, '')
}

export function detectCompressionExtension(fileName: string): string {
  const parts = fileName.split('.')
  return parts.length > 1 ? normalizeCompressionExtension(parts[parts.length - 1] ?? '') : ''
}

export async function resolveCompressionSourceFormat(
  fileName: string,
  mimeType?: string,
): Promise<CompressionSourceFormatDefinition | null> {
  const matrix = await getCompressionCapabilityMatrix()
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

  const extension = detectCompressionExtension(fileName)
  if (!extension) {
    return null
  }

  return (
    matrix.sourceFormats.find(
      (definition) => definition.extension === extension || definition.aliases.includes(extension),
    ) ?? null
  )
}

export async function resolveCompressionTargetFormat(
  extension: string,
): Promise<CompressionTargetFormatDefinition | null> {
  const matrix = await getCompressionCapabilityMatrix()
  const normalizedExtension = normalizeCompressionExtension(extension)

  return (
    matrix.targetFormats.find((definition) => definition.extension === normalizedExtension) ?? null
  )
}

export async function listCompressionTargetsForSource(
  fileName: string,
  mimeType?: string,
): Promise<CompressionTargetFormatDefinition[]> {
  const [matrix, source] = await Promise.all([
    getCompressionCapabilityMatrix(),
    resolveCompressionSourceFormat(fileName, mimeType),
  ])
  if (!source) {
    return []
  }

  return matrix.targetFormats.filter(
    (target) => source.targetExtensions.includes(target.extension) && target.available,
  )
}
