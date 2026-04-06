import { getProcessingCapabilityScope } from '../../processing/application/processing-client'

export type PdfToolkitSourceRouteKind = 'direct-pdf' | 'convert-to-pdf'

export interface PdfToolkitSourceDefinition {
  extension: string
  aliases: string[]
  label: string
  family: string
  routeKind: PdfToolkitSourceRouteKind
  routeLabel: string
  notes: string
  accents: string[]
  available: boolean
  availabilityDetail: string | null
  requiredJobTypes: string[]
}

export interface PdfToolkitOperationDefinition {
  id: 'merge' | 'split' | 'rotate' | 'reorder' | 'ocr' | 'sign' | 'redact' | 'protect' | 'unlock'
  label: string
  detail: string
  statusLabel: string
  accents: string[]
  available: boolean
  availabilityDetail: string | null
  supportsMultiSource: boolean
  requiresPageSelection: boolean
  producesArchive: boolean
  producesPreviewPdf: boolean
  requiredJobTypes: string[]
}

export interface PdfToolkitCapabilityMatrix {
  acceptAttribute: string
  importAcceptAttribute: string
  directSourceFormats: PdfToolkitSourceDefinition[]
  importSourceFormats: PdfToolkitSourceDefinition[]
  operations: PdfToolkitOperationDefinition[]
}

export async function getPdfToolkitCapabilityMatrix(): Promise<PdfToolkitCapabilityMatrix> {
  const scope = await getProcessingCapabilityScope('pdf-toolkit')
  const matrix = scope.pdfToolkitMatrix as PdfToolkitCapabilityMatrix | null | undefined

  if (!matrix) {
    throw new Error('Не удалось загрузить доступные операции PDF Toolkit.')
  }

  return {
    acceptAttribute: matrix.acceptAttribute || '',
    importAcceptAttribute: matrix.importAcceptAttribute || '',
    directSourceFormats: Array.isArray(matrix.directSourceFormats)
      ? matrix.directSourceFormats
      : [],
    importSourceFormats: Array.isArray(matrix.importSourceFormats)
      ? matrix.importSourceFormats
      : [],
    operations: Array.isArray(matrix.operations) ? matrix.operations : [],
  }
}

export async function getPdfToolkitAcceptAttribute(): Promise<string> {
  const matrix = await getPdfToolkitCapabilityMatrix()
  return matrix.acceptAttribute
}

export async function getPdfToolkitImportAcceptAttribute(): Promise<string> {
  const matrix = await getPdfToolkitCapabilityMatrix()
  return matrix.importAcceptAttribute
}

export async function getPdfToolkitOperations(): Promise<PdfToolkitOperationDefinition[]> {
  const matrix = await getPdfToolkitCapabilityMatrix()
  return matrix.operations
}

export function detectPdfToolkitExtension(fileName: string): string {
  const parts = fileName.split('.')
  return parts.length > 1 ? normalizePdfToolkitExtension(parts[parts.length - 1] ?? '') : ''
}

export function normalizePdfToolkitExtension(rawValue: string): string {
  return rawValue.trim().toLowerCase().replace(/^\./u, '')
}

export async function resolvePdfToolkitDirectSource(
  fileName: string,
  mimeType?: string,
): Promise<PdfToolkitSourceDefinition | null> {
  const matrix = await getPdfToolkitCapabilityMatrix()
  return resolveSourceFromList(matrix.directSourceFormats, fileName, mimeType)
}

export async function resolvePdfToolkitImportSource(
  fileName: string,
  mimeType?: string,
): Promise<PdfToolkitSourceDefinition | null> {
  const matrix = await getPdfToolkitCapabilityMatrix()
  return resolveSourceFromList(matrix.importSourceFormats, fileName, mimeType)
}

function resolveSourceFromList(
  sources: PdfToolkitSourceDefinition[],
  fileName: string,
  mimeType?: string,
): PdfToolkitSourceDefinition | null {
  const normalizedMimeType = mimeType?.trim().toLowerCase()

  if (normalizedMimeType === 'application/pdf') {
    const pdfMatch = sources.find((source) => source.extension === 'pdf')
    if (pdfMatch) {
      return pdfMatch
    }
  }

  const extension = detectPdfToolkitExtension(fileName)
  if (!extension) {
    return null
  }

  return (
    sources.find(
      (source) => source.extension === extension || source.aliases.includes(extension),
    ) ?? null
  )
}
