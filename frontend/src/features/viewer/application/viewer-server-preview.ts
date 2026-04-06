import {
  requestProcessingBlob,
  requestProcessingJson,
  runProcessingJob,
} from '../../processing/application/processing-client'
import type { ViewerAudioFact, ViewerAudioLayout } from './viewer-audio'
import type {
  ViewerDocumentEditableDraft,
  ViewerDocumentFact,
  ViewerDocumentLayout,
} from './viewer-document'
import type { ViewerMetadataGroup, ViewerMetadataPayload } from './viewer-metadata'
import type { ViewerVideoFact, ViewerVideoLayout } from './viewer-video'

type ViewerProgressReporter = (message: string) => void

interface ViewerServerBinaryArtifact {
  kind: string
  fileName: string
  mediaType: string
  sizeBytes: number
  downloadPath: string
}

type ViewerServerDocumentLayout =
  | {
      mode: 'pdf'
      pageCount: number | null
      editableDraft: ViewerDocumentEditableDraft | null
    }
  | {
      mode: 'text'
      text: string | null
      paragraphs: string[] | null
      editableDraft: ViewerDocumentEditableDraft | null
    }
  | {
      mode: 'table'
      text: string | null
      table: Extract<ViewerDocumentLayout, { mode: 'table' }>['table']
      editableDraft: ViewerDocumentEditableDraft | null
    }
  | {
      mode: 'html'
      text: string | null
      srcDoc: string | null
      outline: Extract<ViewerDocumentLayout, { mode: 'html' }>['outline']
      editableDraft: ViewerDocumentEditableDraft | null
    }
  | {
      mode: 'workbook'
      text: string | null
      sheets: Extract<ViewerDocumentLayout, { mode: 'workbook' }>['sheets']
      activeSheetIndex: number | null
      editableDraft: ViewerDocumentEditableDraft | null
    }
  | {
      mode: 'slides'
      text: string | null
      slides: Extract<ViewerDocumentLayout, { mode: 'slides' }>['slides']
      editableDraft: ViewerDocumentEditableDraft | null
    }
  | {
      mode: 'database'
      text: string | null
      tables: Extract<ViewerDocumentLayout, { mode: 'database' }>['tables']
      activeTableIndex: number | null
      editableDraft: ViewerDocumentEditableDraft | null
    }

interface ViewerServerImagePayload {
  width: number | null
  height: number | null
  metadata: ViewerMetadataPayload
  warnings: string[]
}

interface ViewerServerDocumentPayload {
  summary: ViewerDocumentFact[]
  searchableText: string
  warnings: string[]
  layout: ViewerServerDocumentLayout
}

interface ViewerServerVideoPayload {
  summary: ViewerVideoFact[]
  warnings: string[]
  layout: Omit<ViewerVideoLayout, 'objectUrl'>
}

interface ViewerServerAudioPayload {
  summary: ViewerAudioFact[]
  warnings: string[]
  searchableText: string
  artworkDataUrl: string | null
  metadataGroups: ViewerMetadataGroup[]
  layout: Omit<ViewerAudioLayout, 'objectUrl'>
}

interface ViewerServerResolveManifest {
  uploadId: string
  originalFileName: string
  family: 'image' | 'document' | 'media' | 'audio'
  kind: 'image' | 'document' | 'video' | 'audio'
  previewLabel: string
  binaryArtifact: ViewerServerBinaryArtifact | null
  imagePayload: ViewerServerImagePayload | null
  documentPayload: ViewerServerDocumentPayload | null
  videoPayload: ViewerServerVideoPayload | null
  audioPayload: ViewerServerAudioPayload | null
}

export type ViewerServerResolvedPayload =
  | {
      kind: 'image'
      objectUrl: string
      dimensions: {
        width: number
        height: number
      }
      metadata: ViewerMetadataPayload
      previewLabel: string
    }
  | {
      kind: 'document'
      summary: ViewerDocumentFact[]
      searchableText: string
      warnings: string[]
      layout: ViewerDocumentLayout
      previewLabel: string
    }
  | {
      kind: 'video'
      summary: ViewerVideoFact[]
      warnings: string[]
      layout: ViewerVideoLayout
      previewLabel: string
    }
  | {
      kind: 'audio'
      summary: ViewerAudioFact[]
      warnings: string[]
      searchableText: string
      artworkDataUrl: string | null
      metadataGroups: ViewerMetadataGroup[]
      layout: ViewerAudioLayout
      previewLabel: string
    }

const VIEWER_RESOLVE_JOB_TYPE = 'VIEWER_RESOLVE'

export async function resolveServerViewerPreview(
  file: File,
  reportProgress?: ViewerProgressReporter,
): Promise<ViewerServerResolvedPayload> {
  const completedJob = await runProcessingJob({
    scope: 'viewer',
    file,
    jobType: VIEWER_RESOLVE_JOB_TYPE,
    reportProgress,
    createMessage: 'Подготавливаю просмотр...',
    timeoutMessage:
      'Подготовка просмотра заняла слишком много времени. Попробуй файл меньшего размера или повтори позже.',
  })

  const manifestArtifact = completedJob.artifacts.find(
    (artifact) => artifact.kind === 'viewer-resolve-manifest',
  )

  if (!manifestArtifact) {
    throw new Error('Просмотр завершился без файла описания результата.')
  }

  reportProgress?.('Загружаю данные для просмотра...')
  const manifest = await requestProcessingJson<ViewerServerResolveManifest>(
    manifestArtifact.downloadPath,
  )

  switch (manifest.kind) {
    case 'image':
      return buildImagePayload(manifest)
    case 'document':
      return buildDocumentPayload(manifest)
    case 'video':
      return buildVideoPayload(manifest)
    case 'audio':
      return buildAudioPayload(manifest)
  }
}

async function buildImagePayload(
  manifest: ViewerServerResolveManifest,
): Promise<ViewerServerResolvedPayload> {
  if (!manifest.imagePayload) {
    throw new Error('Не удалось получить данные изображения.')
  }

  const binaryArtifact = requireBinaryArtifact(manifest, 'image')
  const previewBlob = await requestProcessingBlob(binaryArtifact.downloadPath)
  const objectUrl = URL.createObjectURL(previewBlob)

  return {
    kind: 'image',
    objectUrl,
    dimensions: {
      width: manifest.imagePayload.width ?? 0,
      height: manifest.imagePayload.height ?? 0,
    },
    metadata: manifest.imagePayload.metadata,
    previewLabel: manifest.previewLabel,
  }
}

async function buildDocumentPayload(
  manifest: ViewerServerResolveManifest,
): Promise<ViewerServerResolvedPayload> {
  if (!manifest.documentPayload) {
    throw new Error('Не удалось получить данные документа.')
  }

  let previewObjectUrl: string | null = null

  if (manifest.documentPayload.layout.mode === 'pdf') {
    const binaryArtifact = requireBinaryArtifact(manifest, 'document')
    const previewBlob = await requestProcessingBlob(binaryArtifact.downloadPath)
    previewObjectUrl = URL.createObjectURL(previewBlob)
  }

  try {
    return {
      kind: 'document',
      summary: manifest.documentPayload.summary,
      searchableText: manifest.documentPayload.searchableText,
      warnings: deduplicateWarnings(manifest.documentPayload.warnings),
      layout: mapDocumentLayout(manifest.documentPayload.layout, previewObjectUrl),
      previewLabel: manifest.previewLabel,
    }
  } catch (error) {
    if (previewObjectUrl) {
      URL.revokeObjectURL(previewObjectUrl)
    }

    throw error
  }
}

async function buildVideoPayload(
  manifest: ViewerServerResolveManifest,
): Promise<ViewerServerResolvedPayload> {
  if (!manifest.videoPayload) {
    throw new Error('Не удалось получить данные видео.')
  }

  const binaryArtifact = requireBinaryArtifact(manifest, 'video')
  const previewBlob = await requestProcessingBlob(binaryArtifact.downloadPath)
  const objectUrl = URL.createObjectURL(previewBlob)

  return {
    kind: 'video',
    summary: manifest.videoPayload.summary,
    warnings: deduplicateWarnings(manifest.videoPayload.warnings),
    layout: {
      ...manifest.videoPayload.layout,
      objectUrl,
    },
    previewLabel: manifest.previewLabel,
  }
}

async function buildAudioPayload(
  manifest: ViewerServerResolveManifest,
): Promise<ViewerServerResolvedPayload> {
  if (!manifest.audioPayload) {
    throw new Error('Не удалось получить данные аудио.')
  }

  const binaryArtifact = requireBinaryArtifact(manifest, 'audio')
  const previewBlob = await requestProcessingBlob(binaryArtifact.downloadPath)
  const objectUrl = URL.createObjectURL(previewBlob)

  return {
    kind: 'audio',
    summary: manifest.audioPayload.summary,
    warnings: deduplicateWarnings(manifest.audioPayload.warnings),
    searchableText: manifest.audioPayload.searchableText,
    artworkDataUrl: manifest.audioPayload.artworkDataUrl,
    metadataGroups: manifest.audioPayload.metadataGroups,
    layout: {
      ...manifest.audioPayload.layout,
      objectUrl,
      metadata: {
        ...manifest.audioPayload.layout.metadata,
        mimeType:
          manifest.audioPayload.layout.metadata.mimeType ||
          previewBlob.type ||
          binaryArtifact.mediaType,
      },
    },
    previewLabel: manifest.previewLabel,
  }
}

function requireBinaryArtifact(
  manifest: ViewerServerResolveManifest,
  _kind: ViewerServerResolveManifest['kind'],
): ViewerServerBinaryArtifact {
  if (!manifest.binaryArtifact) {
    throw new Error('Не удалось получить файл для предпросмотра.')
  }

  return manifest.binaryArtifact
}

function mapDocumentLayout(
  layout: ViewerServerDocumentLayout,
  previewObjectUrl: string | null,
): ViewerDocumentLayout {
  switch (layout.mode) {
    case 'pdf':
      if (!previewObjectUrl) {
        throw new Error('Для PDF-предпросмотра не хватает файла результата.')
      }

      return {
        mode: 'pdf',
        objectUrl: previewObjectUrl,
        pageCount: layout.pageCount ?? null,
        editableDraft: layout.editableDraft ?? null,
      }
    case 'text':
      return {
        mode: 'text',
        text: layout.text ?? '',
        paragraphs: layout.paragraphs ?? [],
        editableDraft: layout.editableDraft ?? null,
      }
    case 'table':
      return {
        mode: 'table',
        text: layout.text ?? '',
        table: layout.table,
        editableDraft: layout.editableDraft ?? null,
      }
    case 'html':
      if (typeof layout.srcDoc !== 'string') {
        throw new Error('HTML layout требует srcDoc payload.')
      }

      return {
        mode: 'html',
        text: layout.text ?? '',
        srcDoc: layout.srcDoc,
        outline: layout.outline ?? [],
        editableDraft: layout.editableDraft ?? null,
      }
    case 'workbook':
      return {
        mode: 'workbook',
        text: layout.text ?? '',
        sheets: layout.sheets ?? [],
        activeSheetIndex: layout.activeSheetIndex ?? 0,
        editableDraft: layout.editableDraft ?? null,
      }
    case 'slides':
      return {
        mode: 'slides',
        text: layout.text ?? '',
        slides: layout.slides ?? [],
        editableDraft: layout.editableDraft ?? null,
      }
    case 'database':
      return {
        mode: 'database',
        text: layout.text ?? '',
        tables: layout.tables ?? [],
        activeTableIndex: layout.activeTableIndex ?? 0,
        editableDraft: layout.editableDraft ?? null,
      }
  }
}

function deduplicateWarnings(warnings: string[]): string[] {
  return Array.from(new Set(warnings.filter(Boolean)))
}
