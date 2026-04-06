import { getProcessingCapabilityScope } from '../../processing/application/processing-client'

export type EditorPreviewMode = 'rendered' | 'sandbox' | 'syntax' | 'structured' | 'text'

export interface EditorFormatDefinition {
  id: string
  label: string
  extensions: string[]
  mimeTypes: string[]
  syntaxMode: string
  previewMode: EditorPreviewMode
  supportsFormatting: boolean
  supportsPlainTextExport: boolean
  statusLabel: string
  notes: string
  accents: string[]
  available: boolean
  availabilityDetail: string | null
  requiredJobTypes: string[]
}

export interface EditorCapabilityMatrix {
  acceptAttribute: string
  formats: EditorFormatDefinition[]
}

export function normalizeEditorExtension(rawValue: string): string {
  return rawValue.trim().toLowerCase().replace(/^\./u, '')
}

export function detectEditorExtension(fileName: string): string {
  const parts = fileName.split('.')
  return parts.length > 1 ? normalizeEditorExtension(parts[parts.length - 1] ?? '') : ''
}

export async function getEditorCapabilityMatrix(): Promise<EditorCapabilityMatrix> {
  const scope = await getProcessingCapabilityScope('editor')
  const matrix = scope.editorMatrix as EditorCapabilityMatrix | null | undefined

  if (!matrix) {
    throw new Error('Не удалось загрузить доступные форматы редактора.')
  }

  return {
    acceptAttribute: matrix.acceptAttribute || '',
    formats: Array.isArray(matrix.formats) ? matrix.formats : [],
  }
}

export async function getEditorAcceptAttribute(): Promise<string> {
  const matrix = await getEditorCapabilityMatrix()
  return matrix.acceptAttribute
}

export async function listEditorFormats(): Promise<EditorFormatDefinition[]> {
  const matrix = await getEditorCapabilityMatrix()
  return matrix.formats
}

export async function resolveEditorFormatById(
  formatId: string,
): Promise<EditorFormatDefinition | null> {
  const matrix = await getEditorCapabilityMatrix()
  const normalizedId = formatId.trim().toLowerCase()

  return matrix.formats.find((format) => format.id === normalizedId) ?? null
}

export async function resolveEditorFormat(
  fileName: string,
  mimeType?: string,
): Promise<EditorFormatDefinition | null> {
  const matrix = await getEditorCapabilityMatrix()
  const normalizedMimeType = mimeType?.trim().toLowerCase()

  if (normalizedMimeType) {
    const matchByMime = matrix.formats.find((format) =>
      format.mimeTypes.some((candidate) => candidate.trim().toLowerCase() === normalizedMimeType),
    )
    if (matchByMime) {
      return matchByMime
    }
  }

  const extension = detectEditorExtension(fileName)
  if (!extension) {
    return null
  }

  return (
    matrix.formats.find((format) =>
      format.extensions.some((candidate) => normalizeEditorExtension(candidate) === extension),
    ) ?? null
  )
}
