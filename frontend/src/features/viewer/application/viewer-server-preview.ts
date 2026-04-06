import {
  requestProcessingBlob,
  requestProcessingJson,
  runProcessingJob,
} from '../../processing/application/processing-client'
import type {
  ViewerAudioFact,
  ViewerAudioLayout,
} from './viewer-audio'
import type {
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
    }
  | {
      mode: 'text'
      text: string | null
      paragraphs: string[] | null
    }
  | {
      mode: 'table'
      text: string | null
      table: Extract<ViewerDocumentLayout, { mode: 'table' }>['table']
    }
  | {
      mode: 'html'
      text: string | null
      srcDoc: string | null
      outline: Extract<ViewerDocumentLayout, { mode: 'html' }>['outline']
    }
  | {
      mode: 'workbook'
      text: string | null
      sheets: Extract<ViewerDocumentLayout, { mode: 'workbook' }>['sheets']
      activeSheetIndex: number | null
    }
  | {
      mode: 'slides'
      text: string | null
      slides: Extract<ViewerDocumentLayout, { mode: 'slides' }>['slides']
    }
  | {
      mode: 'database'
      text: string | null
      tables: Extract<ViewerDocumentLayout, { mode: 'database' }>['tables']
      activeTableIndex: number | null
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
    createMessage: 'Создаю backend VIEWER_RESOLVE job...',
    timeoutMessage:
      'Backend VIEWER_RESOLVE job не завершился в ожидаемое время. Попробуй файл меньшего размера или проверь backend logs.',
  })

  const manifestArtifact = completedJob.artifacts.find(
    (artifact) => artifact.kind === 'viewer-resolve-manifest',
  )

  if (!manifestArtifact) {
    throw new Error('Backend VIEWER_RESOLVE job завершился без unified viewer manifest.')
  }

  reportProgress?.('Загружаю unified viewer manifest и связанные artifacts с backend...')
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
    throw new Error('Backend VIEWER_RESOLVE не вернул image payload.')
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
    throw new Error('Backend VIEWER_RESOLVE не вернул document payload.')
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
    throw new Error('Backend VIEWER_RESOLVE не вернул video payload.')
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
    throw new Error('Backend VIEWER_RESOLVE не вернул audio payload.')
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
          manifest.audioPayload.layout.metadata.mimeType || previewBlob.type || binaryArtifact.mediaType,
      },
    },
    previewLabel: manifest.previewLabel,
  }
}

function requireBinaryArtifact(
  manifest: ViewerServerResolveManifest,
  kind: ViewerServerResolveManifest['kind'],
): ViewerServerBinaryArtifact {
  if (!manifest.binaryArtifact) {
    throw new Error(`Backend VIEWER_RESOLVE не вернул binary artifact для ${kind} preview.`)
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
        throw new Error('PDF layout требует backend binary artifact.')
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
      return {
        mode: 'table',
        text: layout.text ?? '',
        table: layout.table,
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

function deduplicateWarnings(warnings: string[]): string[] {
  return Array.from(new Set(warnings.filter(Boolean)))
}
