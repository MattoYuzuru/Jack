import {
  awaitProcessingJob,
  createProcessingJob,
  ensureProcessingCapability,
  requestProcessingBlob,
  requestProcessingJson,
  uploadProcessingFile,
  type ProcessingArtifact,
  type ProcessingJobResponse,
} from '../../processing/application/processing-client'

export interface CompressionFact {
  label: string
  value: string
}

export interface CompressionAttempt {
  label: string
  targetExtension: string
  resultSizeBytes: number
  targetMet: boolean
  maxWidth: number | null
  maxHeight: number | null
  quality: number | null
  targetFps: number | null
  videoBitrateKbps: number | null
  audioBitrateKbps: number | null
  runtimeLabel: string
}

export interface ServerCompressionManifest {
  uploadId: string
  originalFileName: string
  sourceExtension: string
  family: 'image' | 'media' | 'audio'
  mode: 'MAX_REDUCTION' | 'TARGET_SIZE' | 'CUSTOM'
  sourceSizeBytes: number
  targetSizeBytes: number | null
  targetMet: boolean
  resultSizeBytes: number
  resultFileName: string
  previewFileName: string
  targetExtension: string
  resultMediaType: string
  previewMediaType: string
  previewKind: 'image' | 'media'
  sourceAdapterLabel: string
  targetAdapterLabel: string
  runtimeLabel: string
  sourceFacts: CompressionFact[]
  resultFacts: CompressionFact[]
  compressionFacts: CompressionFact[]
  attempts: CompressionAttempt[]
  warnings: string[]
  generatedAt: string
}

export interface ServerCompressionResult {
  job: ProcessingJobResponse
  manifestArtifact: ProcessingArtifact
  resultArtifact: ProcessingArtifact
  previewArtifact: ProcessingArtifact
  manifest: ServerCompressionManifest
  resultBlob: Blob
  previewBlob: Blob
}

export interface RunServerCompressionInput {
  file: File
  mode: 'maximum' | 'target-size' | 'custom'
  targetSizeBytes?: number | null
  targetExtension?: string | null
  maxWidth?: number | null
  maxHeight?: number | null
  quality?: number | null
  backgroundColor?: string | null
  targetFps?: number | null
  videoBitrateKbps?: number | null
  audioBitrateKbps?: number | null
  presetLabel?: string | null
  reportProgress?: (message: string) => void
  onJobCreated?: (job: ProcessingJobResponse) => void
  onJobUpdate?: (job: ProcessingJobResponse) => void
}

const FILE_COMPRESS_JOB_TYPE = 'FILE_COMPRESS'

export async function runServerCompression(
  input: RunServerCompressionInput,
): Promise<ServerCompressionResult> {
  input.reportProgress?.('Проверяю доступность сжатия...')
  await ensureProcessingCapability('compression', FILE_COMPRESS_JOB_TYPE)

  input.reportProgress?.('Загружаю исходный файл...')
  const upload = await uploadProcessingFile(input.file)

  input.reportProgress?.('Запускаю подбор оптимального результата...')
  const createdJob = await createProcessingJob({
    uploadId: upload.id,
    jobType: FILE_COMPRESS_JOB_TYPE,
    parameters: {
      mode: input.mode,
      targetSizeBytes: input.targetSizeBytes,
      targetExtension: input.targetExtension,
      maxWidth: input.maxWidth,
      maxHeight: input.maxHeight,
      quality: input.quality,
      backgroundColor: input.backgroundColor,
      targetFps: input.targetFps,
      videoBitrateKbps: input.videoBitrateKbps,
      audioBitrateKbps: input.audioBitrateKbps,
      presetLabel: input.presetLabel,
    },
  })
  input.onJobCreated?.(createdJob)

  const completedJob = await awaitProcessingJob(createdJob.id, {
    reportProgress: input.reportProgress,
    timeoutMessage:
      'Сжатие заняло слишком много времени. Попробуй смягчить ограничения или повтори позже.',
    onUpdate: input.onJobUpdate,
  })

  input.reportProgress?.('Загружаю результат и предпросмотр...')
  return downloadServerCompressionArtifacts(completedJob)
}

export async function downloadServerCompressionArtifacts(
  job: ProcessingJobResponse,
): Promise<ServerCompressionResult> {
  const manifestArtifact = job.artifacts.find(
    (artifact) => artifact.kind === 'compression-manifest',
  )
  const resultArtifact = job.artifacts.find((artifact) => artifact.kind === 'compression-binary')
  const previewArtifact = job.artifacts.find((artifact) => artifact.kind === 'compression-preview')

  if (!manifestArtifact || !resultArtifact || !previewArtifact) {
    throw new Error('Сжатие завершилось без обязательных файлов результата.')
  }

  const [manifest, resultBlob, previewBlob] = await Promise.all([
    requestProcessingJson<ServerCompressionManifest>(manifestArtifact.downloadPath),
    requestProcessingBlob(resultArtifact.downloadPath),
    requestProcessingBlob(previewArtifact.downloadPath),
  ])

  return {
    job,
    manifestArtifact,
    resultArtifact,
    previewArtifact,
    manifest,
    resultBlob,
    previewBlob,
  }
}
