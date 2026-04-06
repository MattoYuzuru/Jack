import { getProcessingCapabilityScope } from '../../processing/application/processing-client'

export type ViewerFormatFamily = 'image' | 'document' | 'media' | 'audio' | 'data'

export type ViewerPreviewPipeline =
  | 'browser-native'
  | 'client-decode'
  | 'server-assisted'
  | 'planned'

export type PreviewStrategyId =
  | 'native-image'
  | 'native-video'
  | 'native-audio'
  | 'server-viewer'
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
  available: boolean
  availabilityDetail: string | null
  requiredJobTypes: string[]
}

export interface ViewerCapabilityMatrix {
  acceptAttribute: string
  formats: ViewerFormatDefinition[]
}

export async function getViewerCapabilityMatrix(): Promise<ViewerCapabilityMatrix> {
  const scope = await getProcessingCapabilityScope('viewer')
  const matrix = scope.viewerMatrix as ViewerCapabilityMatrix | null | undefined

  if (!matrix) {
    throw new Error('Backend viewer capability matrix не вернула viewerMatrix payload.')
  }

  return {
    acceptAttribute: matrix.acceptAttribute || '',
    formats: Array.isArray(matrix.formats) ? matrix.formats : [],
  }
}

export async function getViewerAcceptAttribute(): Promise<string> {
  const matrix = await getViewerCapabilityMatrix()
  return matrix.acceptAttribute
}

export async function listViewerFormatsByFamily(
  family: ViewerFormatFamily,
): Promise<ViewerFormatDefinition[]> {
  const matrix = await getViewerCapabilityMatrix()
  return matrix.formats.filter((definition) => definition.family === family)
}

export function normalizeExtension(rawValue: string): string {
  return rawValue.trim().toLowerCase().replace(/^\./u, '')
}

export function detectFileExtension(fileName: string): string {
  const parts = fileName.split('.')
  return parts.length > 1 ? normalizeExtension(parts[parts.length - 1] ?? '') : ''
}

export async function resolveViewerFormat(
  fileName: string,
  mimeType?: string,
): Promise<ViewerFormatDefinition | null> {
  const matrix = await getViewerCapabilityMatrix()
  const normalizedMimeType = mimeType?.trim().toLowerCase()

  if (normalizedMimeType) {
    const matchByMime = matrix.formats.find((definition) =>
      definition.mimeTypes.some((candidate) => candidate.trim().toLowerCase() === normalizedMimeType),
    )

    if (matchByMime) {
      return matchByMime
    }
  }

  const extension = detectFileExtension(fileName)
  if (!extension) {
    return null
  }

  return (
    matrix.formats.find(
      (definition) =>
        definition.extension === extension || definition.aliases.includes(extension),
    ) ?? null
  )
}
