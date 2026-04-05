import {
  requestProcessingBlob,
  requestProcessingJson,
  runProcessingJob,
  type ProcessingArtifact,
  type ProcessingJobResponse,
} from '../../processing/application/processing-client'
import type {
  ViewerDocumentDatabaseTablePreview,
  ViewerDocumentFact,
  ViewerDocumentLayout,
  ViewerDocumentOutlineItem,
  ViewerDocumentPreviewPayload,
  ViewerDocumentSheetPreview,
  ViewerDocumentSlidePreview,
  ViewerDocumentTablePreview,
} from './viewer-document'

type ViewerProgressReporter = (message: string) => void

type ViewerServerDocumentLayout =
  | {
      mode: 'pdf'
      pageCount: number | null
    }
  | {
      mode: 'text'
      text: string | null
      paragraphs: string[] | null
    }
  | {
      mode: 'table'
      text: string | null
      table: ViewerDocumentTablePreview | null
    }
  | {
      mode: 'html'
      text: string | null
      srcDoc: string | null
      outline: ViewerDocumentOutlineItem[] | null
    }
  | {
      mode: 'workbook'
      text: string | null
      sheets: ViewerDocumentSheetPreview[] | null
      activeSheetIndex: number | null
    }
  | {
      mode: 'slides'
      text: string | null
      slides: ViewerDocumentSlidePreview[] | null
    }
  | {
      mode: 'database'
      text: string | null
      tables: ViewerDocumentDatabaseTablePreview[] | null
      activeTableIndex: number | null
    }

interface ViewerServerDocumentPreviewPayload {
  summary: ViewerDocumentFact[]
  searchableText: string
  warnings: string[]
  layout: ViewerServerDocumentLayout
  previewLabel: string
}

interface ViewerServerDocumentPreviewArtifacts {
  manifest: ViewerServerDocumentPreviewPayload
  previewBlob: Blob | null
}

const DOCUMENT_PREVIEW_JOB_TYPE = 'DOCUMENT_PREVIEW'

export async function buildPdfDocumentPreview(
  file: File,
  reportProgress?: ViewerProgressReporter,
): Promise<ViewerDocumentPreviewPayload> {
  return buildViewerServerDocumentPreview(file, reportProgress)
}

export async function buildTextDocumentPreview(
  file: File,
  reportProgress?: ViewerProgressReporter,
): Promise<ViewerDocumentPreviewPayload> {
  return buildViewerServerDocumentPreview(file, reportProgress)
}

export async function buildCsvDocumentPreview(
  file: File,
  reportProgress?: ViewerProgressReporter,
): Promise<ViewerDocumentPreviewPayload> {
  return buildViewerServerDocumentPreview(file, reportProgress)
}

export async function buildHtmlDocumentPreview(
  file: File,
  reportProgress?: ViewerProgressReporter,
): Promise<ViewerDocumentPreviewPayload> {
  return buildViewerServerDocumentPreview(file, reportProgress)
}

export async function buildRtfDocumentPreview(
  file: File,
  reportProgress?: ViewerProgressReporter,
): Promise<ViewerDocumentPreviewPayload> {
  return buildViewerServerDocumentPreview(file, reportProgress)
}

export async function buildDocDocumentPreview(
  file: File,
  reportProgress?: ViewerProgressReporter,
): Promise<ViewerDocumentPreviewPayload> {
  return buildViewerServerDocumentPreview(file, reportProgress)
}

export async function buildDocxDocumentPreview(
  file: File,
  reportProgress?: ViewerProgressReporter,
): Promise<ViewerDocumentPreviewPayload> {
  return buildViewerServerDocumentPreview(file, reportProgress)
}

export async function buildOdtDocumentPreview(
  file: File,
  reportProgress?: ViewerProgressReporter,
): Promise<ViewerDocumentPreviewPayload> {
  return buildViewerServerDocumentPreview(file, reportProgress)
}

export async function buildXlsDocumentPreview(
  file: File,
  reportProgress?: ViewerProgressReporter,
): Promise<ViewerDocumentPreviewPayload> {
  return buildViewerServerDocumentPreview(file, reportProgress)
}

export async function buildXlsxDocumentPreview(
  file: File,
  reportProgress?: ViewerProgressReporter,
): Promise<ViewerDocumentPreviewPayload> {
  return buildViewerServerDocumentPreview(file, reportProgress)
}

export async function buildPptxDocumentPreview(
  file: File,
  reportProgress?: ViewerProgressReporter,
): Promise<ViewerDocumentPreviewPayload> {
  return buildViewerServerDocumentPreview(file, reportProgress)
}

export async function buildEpubDocumentPreview(
  file: File,
  reportProgress?: ViewerProgressReporter,
): Promise<ViewerDocumentPreviewPayload> {
  return buildViewerServerDocumentPreview(file, reportProgress)
}

export async function buildSqliteDocumentPreview(
  file: File,
  reportProgress?: ViewerProgressReporter,
): Promise<ViewerDocumentPreviewPayload> {
  return buildViewerServerDocumentPreview(file, reportProgress)
}

async function buildViewerServerDocumentPreview(
  file: File,
  reportProgress?: ViewerProgressReporter,
): Promise<ViewerDocumentPreviewPayload> {
  const completedJob = await runProcessingJob({
    scope: 'viewer',
    file,
    jobType: DOCUMENT_PREVIEW_JOB_TYPE,
    reportProgress,
    createMessage: 'Создаю backend DOCUMENT_PREVIEW job...',
    timeoutMessage:
      'Backend DOCUMENT_PREVIEW job не завершился в ожидаемое время. Попробуй файл меньшего размера или проверь backend logs.',
  })

  reportProgress?.('Загружаю document manifest и preview artifact с backend...')
  const { manifest, previewBlob } = await downloadViewerDocumentPreviewArtifacts(completedJob)

  let previewObjectUrl: string | null = null
  if (manifest.layout.mode === 'pdf') {
    if (!previewBlob) {
      throw new Error('Backend DOCUMENT_PREVIEW job завершился без PDF preview artifact.')
    }

    previewObjectUrl = URL.createObjectURL(previewBlob)
  }

  try {
    return {
      summary: manifest.summary,
      searchableText: manifest.searchableText,
      warnings: deduplicateViewerWarnings(manifest.warnings),
      layout: mapViewerDocumentLayout(manifest.layout, previewObjectUrl),
      previewLabel: manifest.previewLabel,
    }
  } catch (error) {
    if (previewObjectUrl) {
      URL.revokeObjectURL(previewObjectUrl)
    }

    throw error
  }
}

async function downloadViewerDocumentPreviewArtifacts(
  job: ProcessingJobResponse,
): Promise<ViewerServerDocumentPreviewArtifacts> {
  const manifestArtifact = job.artifacts.find(
    (artifact) => artifact.kind === 'document-preview-manifest',
  )

  if (!manifestArtifact) {
    throw new Error('Backend DOCUMENT_PREVIEW job завершился без manifest artifact.')
  }

  const manifest = await requestProcessingJson<ViewerServerDocumentPreviewPayload>(
    manifestArtifact.downloadPath,
  )
  const binaryArtifact = resolveViewerDocumentBinaryArtifact(job.artifacts)
  const previewBlob = binaryArtifact
    ? await requestProcessingBlob(binaryArtifact.downloadPath)
    : null

  return {
    manifest,
    previewBlob,
  }
}

function resolveViewerDocumentBinaryArtifact(
  artifacts: ProcessingArtifact[],
): ProcessingArtifact | null {
  return artifacts.find((artifact) => artifact.kind === 'document-preview-binary') ?? null
}

function mapViewerDocumentLayout(
  layout: ViewerServerDocumentLayout,
  previewObjectUrl: string | null,
): ViewerDocumentLayout {
  switch (layout.mode) {
    case 'pdf':
      if (!previewObjectUrl) {
        throw new Error('PDF preview требует готовый backend binary artifact.')
      }

      return {
        mode: 'pdf',
        objectUrl: previewObjectUrl,
        pageCount: layout.pageCount ?? null,
      }
    case 'text':
      return {
        mode: 'text',
        text: layout.text ?? '',
        paragraphs: layout.paragraphs ?? [],
      }
    case 'table':
      if (!layout.table) {
        throw new Error('Backend DOCUMENT_PREVIEW вернул table layout без table payload.')
      }

      return {
        mode: 'table',
        text: layout.text ?? '',
        table: layout.table,
      }
    case 'html':
      if (typeof layout.srcDoc !== 'string') {
        throw new Error('Backend DOCUMENT_PREVIEW вернул html layout без srcDoc payload.')
      }

      return {
        mode: 'html',
        text: layout.text ?? '',
        srcDoc: layout.srcDoc,
        outline: layout.outline ?? [],
      }
    case 'workbook':
      return {
        mode: 'workbook',
        text: layout.text ?? '',
        sheets: layout.sheets ?? [],
        activeSheetIndex: layout.activeSheetIndex ?? 0,
      }
    case 'slides':
      return {
        mode: 'slides',
        text: layout.text ?? '',
        slides: layout.slides ?? [],
      }
    case 'database':
      return {
        mode: 'database',
        text: layout.text ?? '',
        tables: layout.tables ?? [],
        activeTableIndex: layout.activeTableIndex ?? 0,
      }
  }
}

function deduplicateViewerWarnings(warnings: string[]): string[] {
  return Array.from(new Set(warnings.filter(Boolean)))
}
