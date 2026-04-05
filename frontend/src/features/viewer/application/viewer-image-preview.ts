import {
  requestProcessingBlob,
  requestProcessingJson,
  runProcessingJob,
  type ProcessingJobResponse,
} from '../../processing/application/processing-client'
import { loadStructuredMetadata, type ViewerBinaryPreview } from './viewer-preview'

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
  _buffer: ArrayBuffer,
  context: ViewerImagePreviewContext,
): Promise<ViewerBinaryPreview> {
  const metadata = await loadStructuredMetadata(context.file)
  return buildServerImagePreview(context, metadata)
}

export async function decodeTiffPreview(
  _buffer: ArrayBuffer,
  context: ViewerImagePreviewContext,
): Promise<ViewerBinaryPreview> {
  const metadata = await loadStructuredMetadata(context.file)
  return buildServerImagePreview(context, metadata)
}

export async function decodeRawPreview(
  _buffer: ArrayBuffer,
  context: ViewerImagePreviewContext,
): Promise<ViewerBinaryPreview> {
  const metadata = await loadStructuredMetadata(context.file)
  return buildServerImagePreview(context, metadata)
}

async function buildServerImagePreview(
  context: ViewerImagePreviewContext,
  metadata: ViewerBinaryPreview['metadata'],
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
