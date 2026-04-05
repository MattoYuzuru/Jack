import {
  requestProcessingBlob,
  requestProcessingJson,
  runProcessingJob,
  type ProcessingJobResponse,
} from '../../processing/application/processing-client'
import {
  extractRawFallbackMetadata,
  loadStructuredMetadata,
  type ViewerBinaryPreview,
} from './viewer-preview'
import { createEmptyMetadataPayload, mergeMetadataPayload } from './viewer-metadata'

interface ViewerImagePreviewManifest {
  operation: string
  sourceAdapterLabel: string
  targetAdapterLabel: string
  runtimeLabel: string
  previewMediaType: string
  warnings: string[]
}

interface ViewerServerImagePreviewArtifacts {
  manifest: ViewerImagePreviewManifest
  previewBlob: Blob
}

interface ViewerImagePreviewContext {
  file: File
  reportProgress?: (message: string) => void
}

const IMAGE_PREVIEW_JOB_TYPE = 'IMAGE_CONVERT'

export async function decodeHeicPreview(
  buffer: ArrayBuffer,
  context: ViewerImagePreviewContext,
): Promise<ViewerBinaryPreview> {
  const metadata = await loadStructuredMetadata(buffer)
  return buildServerImagePreview(context, metadata)
}

export async function decodeTiffPreview(
  buffer: ArrayBuffer,
  context: ViewerImagePreviewContext,
): Promise<ViewerBinaryPreview> {
  const metadata = await loadStructuredMetadata(buffer)
  return buildServerImagePreview(context, metadata)
}

export async function decodeRawPreview(
  buffer: ArrayBuffer,
  context: ViewerImagePreviewContext,
): Promise<ViewerBinaryPreview> {
  const fallbackEntries = extractRawFallbackMetadata(buffer)
  const metadata = mergeMetadataPayload(
    await loadStructuredMetadata(buffer),
    fallbackEntries,
    fallbackEntries.length
      ? [
          {
            id: 'raw-fallback',
            label: 'RAW Fallback',
            entries: fallbackEntries,
          },
        ]
      : [],
  )

  return buildServerImagePreview(context, metadata)
}

async function buildServerImagePreview(
  context: ViewerImagePreviewContext,
  metadata = createEmptyMetadataPayload(),
): Promise<ViewerBinaryPreview> {
  const completedJob = await runProcessingJob({
    scope: 'viewer',
    file: context.file,
    jobType: IMAGE_PREVIEW_JOB_TYPE,
    parameters: {
      operation: 'preview',
      maxWidth: 4096,
      maxHeight: 4096,
    },
    reportProgress: context.reportProgress,
    createMessage: 'Создаю backend IMAGE_CONVERT job для image preview...',
    timeoutMessage:
      'Backend IMAGE_CONVERT job не завершился в ожидаемое время. Попробуй файл меньшего размера или проверь backend logs.',
  })

  const imagePreview = await downloadViewerImagePreviewArtifacts(completedJob)
  const bytes = new Uint8Array(await imagePreview.previewBlob.arrayBuffer())

  return {
    bytes,
    mimeType:
      imagePreview.manifest.previewMediaType ||
      imagePreview.previewBlob.type ||
      'image/png',
    metadata,
    previewLabel: `Backend image preview · ${imagePreview.manifest.sourceAdapterLabel}`,
  }
}

async function downloadViewerImagePreviewArtifacts(
  job: ProcessingJobResponse,
): Promise<ViewerServerImagePreviewArtifacts> {
  const manifestArtifact = job.artifacts.find(
    (artifact) => artifact.kind === 'image-preview-manifest',
  )
  const previewArtifact = job.artifacts.find((artifact) => artifact.kind === 'image-preview-binary')

  if (!manifestArtifact || !previewArtifact) {
    throw new Error('Backend IMAGE_CONVERT preview job завершился без обязательных artifacts.')
  }

  const [manifest, previewBlob] = await Promise.all([
    requestProcessingJson<ViewerImagePreviewManifest>(manifestArtifact.downloadPath),
    requestProcessingBlob(previewArtifact.downloadPath),
  ])

  return {
    manifest,
    previewBlob,
  }
}
