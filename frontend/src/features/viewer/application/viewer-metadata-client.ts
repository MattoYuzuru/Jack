import { detectFileExtension } from '../domain/viewer-registry'
import {
  requestProcessingBlob,
  requestProcessingJson,
  runProcessingJob,
} from '../../processing/application/processing-client'
import type { ViewerAudioMetadataPayload } from './viewer-audio'
import type { ViewerEditableMetadata, ViewerMetadataPayload } from './viewer-metadata'

type ViewerMetadataProgressReporter = (message: string) => void

export interface ViewerMetadataExportResult {
  blob: Blob
  fileName: string
  mode: 'embedded-jpeg' | 'json-sidecar'
}

interface ViewerMetadataInspectManifest {
  operation: 'inspect-image' | 'inspect-audio'
  family: 'image' | 'audio'
  imagePayload: ViewerMetadataPayload | null
  audioPayload: ViewerAudioMetadataPayload | null
  warnings: string[]
}

interface ViewerMetadataExportManifest {
  mode: 'embedded-jpeg' | 'json-sidecar'
  fileName: string
  warnings: string[]
}

const METADATA_JOB_TYPE = 'METADATA_EXPORT'

export function canEmbedMetadata(fileName: string): boolean {
  const extension = detectFileExtension(fileName)
  return extension === 'jpg' || extension === 'jpeg'
}

export async function inspectViewerImageMetadata(
  file: File,
  reportProgress?: ViewerMetadataProgressReporter,
): Promise<ViewerMetadataPayload> {
  const manifest = await runMetadataInspectJob(file, 'inspect-image', reportProgress)

  if (!manifest.imagePayload) {
    throw new Error('Не удалось получить метаданные изображения.')
  }

  return manifest.imagePayload
}

export async function inspectViewerAudioMetadata(
  file: File,
  reportProgress?: ViewerMetadataProgressReporter,
): Promise<{ payload: ViewerAudioMetadataPayload; warnings: string[] }> {
  const manifest = await runMetadataInspectJob(file, 'inspect-audio', reportProgress)

  if (!manifest.audioPayload) {
    throw new Error('Не удалось получить метаданные аудио.')
  }

  return {
    payload: manifest.audioPayload,
    warnings: manifest.warnings,
  }
}

export async function exportViewerMetadata(
  file: File,
  metadata: ViewerEditableMetadata,
  reportProgress?: ViewerMetadataProgressReporter,
): Promise<ViewerMetadataExportResult> {
  const completedJob = await runProcessingJob({
    scope: 'viewer',
    file,
    jobType: METADATA_JOB_TYPE,
    parameters: {
      operation: 'export-image',
      metadata,
    },
    reportProgress,
    createMessage: 'Сохраняю метаданные...',
    timeoutMessage:
      'Сохранение метаданных заняло слишком много времени. Попробуй файл меньшего размера или повтори позже.',
  })

  const manifestArtifact = completedJob.artifacts.find(
    (artifact) => artifact.kind === 'metadata-export-manifest',
  )
  const exportArtifact = completedJob.artifacts.find(
    (artifact) => artifact.kind === 'metadata-export-binary',
  )

  if (!manifestArtifact || !exportArtifact) {
    throw new Error('Сохранение метаданных завершилось без файлов результата.')
  }

  reportProgress?.('Загружаю готовый файл...')
  const [manifest, blob] = await Promise.all([
    requestProcessingJson<ViewerMetadataExportManifest>(manifestArtifact.downloadPath),
    requestProcessingBlob(exportArtifact.downloadPath),
  ])

  return {
    blob,
    fileName: manifest.fileName,
    mode: manifest.mode,
  }
}

async function runMetadataInspectJob(
  file: File,
  operation: ViewerMetadataInspectManifest['operation'],
  reportProgress?: ViewerMetadataProgressReporter,
): Promise<ViewerMetadataInspectManifest> {
  // Inspect и export сознательно идут через один backend job type:
  // viewer держит единый контракт, а backend сам решает, какой metadata pipeline нужен.
  const completedJob = await runProcessingJob({
    scope: 'viewer',
    file,
    jobType: METADATA_JOB_TYPE,
    parameters: { operation },
    reportProgress,
    createMessage: 'Считываю метаданные...',
    timeoutMessage:
      'Чтение метаданных заняло слишком много времени. Проверь размер файла и повтори позже.',
  })

  const manifestArtifact = completedJob.artifacts.find(
    (artifact) => artifact.kind === 'metadata-inspect-manifest',
  )

  if (!manifestArtifact) {
    throw new Error('Чтение метаданных завершилось без файла результата.')
  }

  reportProgress?.('Загружаю данные метаданных...')
  return requestProcessingJson<ViewerMetadataInspectManifest>(manifestArtifact.downloadPath)
}
