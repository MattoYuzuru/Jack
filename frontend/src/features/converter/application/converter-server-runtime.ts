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

export interface ConverterFact {
  label: string
  value: string
}

export interface ServerImageConvertManifest {
  operation: string
  sourceWidth: number
  sourceHeight: number
  width: number
  height: number
  resultMediaType: string
  previewMediaType: string
  outputExtension: string
  sourceAdapterLabel: string
  targetAdapterLabel: string
  runtimeLabel: string
  warnings: string[]
}

export interface ServerImageConvertResult {
  job: ProcessingJobResponse
  manifestArtifact: ProcessingArtifact
  resultArtifact: ProcessingArtifact
  previewArtifact: ProcessingArtifact
  manifest: ServerImageConvertManifest
  resultBlob: Blob
  previewBlob: Blob
}

export interface ServerOfficeConvertManifest {
  uploadId: string
  originalFileName: string
  sourceExtension: string
  targetExtension: string
  resultMediaType: string
  previewMediaType: string
  previewKind: 'image' | 'document' | 'media'
  sourceAdapterLabel: string
  targetAdapterLabel: string
  runtimeLabel: string
  sourceFacts: ConverterFact[]
  resultFacts: ConverterFact[]
  warnings: string[]
}

export interface ServerOfficeConvertResult {
  job: ProcessingJobResponse
  manifestArtifact: ProcessingArtifact
  resultArtifact: ProcessingArtifact
  previewArtifact: ProcessingArtifact
  manifest: ServerOfficeConvertManifest
  resultBlob: Blob
  previewBlob: Blob
}

export interface ServerMediaConvertManifest {
  uploadId: string
  originalFileName: string
  sourceExtension: string
  targetExtension: string
  resultMediaType: string
  previewMediaType: string
  previewKind: 'image' | 'document' | 'media'
  sourceAdapterLabel: string
  targetAdapterLabel: string
  runtimeLabel: string
  sourceFacts: ConverterFact[]
  resultFacts: ConverterFact[]
  warnings: string[]
}

export interface ServerMediaConvertResult {
  job: ProcessingJobResponse
  manifestArtifact: ProcessingArtifact
  resultArtifact: ProcessingArtifact
  previewArtifact: ProcessingArtifact
  manifest: ServerMediaConvertManifest
  resultBlob: Blob
  previewBlob: Blob
}

interface RunServerImageConvertInput {
  file: File
  targetExtension: string
  maxWidth: number | null
  maxHeight: number | null
  quality?: number
  backgroundColor?: string
  presetLabel?: string
  reportProgress?: (message: string) => void
  onJobCreated?: (job: ProcessingJobResponse) => void
  onJobUpdate?: (job: ProcessingJobResponse) => void
}

interface RunServerMediaConvertInput extends RunServerImageConvertInput {
  videoCodec?: string
  audioCodec?: string
  targetFps?: number | null
  videoBitrateKbps?: number | null
  audioBitrateKbps?: number | null
}

const IMAGE_CONVERT_JOB_TYPE = 'IMAGE_CONVERT'
const OFFICE_CONVERT_JOB_TYPE = 'OFFICE_CONVERT'
const MEDIA_CONVERT_JOB_TYPE = 'MEDIA_CONVERT'

export async function runServerImageConvert(
  input: RunServerImageConvertInput,
): Promise<ServerImageConvertResult> {
  input.reportProgress?.('Проверяю доступность конвертации изображения...')
  await ensureProcessingCapability('converter', IMAGE_CONVERT_JOB_TYPE)

  input.reportProgress?.('Загружаю исходный файл...')
  const upload = await uploadProcessingFile(input.file)

  input.reportProgress?.('Запускаю конвертацию...')
  const createdJob = await createProcessingJob({
    uploadId: upload.id,
    jobType: IMAGE_CONVERT_JOB_TYPE,
    parameters: {
      operation: 'convert',
      targetExtension: input.targetExtension,
      maxWidth: input.maxWidth,
      maxHeight: input.maxHeight,
      quality: input.quality,
      backgroundColor: input.backgroundColor,
      presetLabel: input.presetLabel,
    },
  })
  input.onJobCreated?.(createdJob)

  const completedJob = await awaitProcessingJob(createdJob.id, {
    reportProgress: input.reportProgress,
    timeoutMessage:
      'Конвертация заняла слишком много времени. Попробуй более компактный профиль или повтори позже.',
    onUpdate: input.onJobUpdate,
  })

  input.reportProgress?.('Загружаю готовый файл и предпросмотр...')
  return downloadServerConvertArtifacts(completedJob)
}

export async function downloadServerConvertArtifacts(
  job: ProcessingJobResponse,
): Promise<ServerImageConvertResult> {
  const manifestArtifact = job.artifacts.find(
    (artifact) => artifact.kind === 'image-convert-manifest',
  )
  const resultArtifact = job.artifacts.find((artifact) => artifact.kind === 'image-convert-binary')
  const previewArtifact = job.artifacts.find(
    (artifact) => artifact.kind === 'image-convert-preview',
  )

  if (!manifestArtifact || !resultArtifact || !previewArtifact) {
    throw new Error('Конвертация завершилась без обязательных файлов результата.')
  }

  const [manifest, resultBlob, previewBlob] = await Promise.all([
    requestProcessingJson<ServerImageConvertManifest>(manifestArtifact.downloadPath),
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

export async function runServerOfficeConvert(
  input: RunServerImageConvertInput,
): Promise<ServerOfficeConvertResult> {
  input.reportProgress?.('Проверяю доступность конвертации документа...')
  await ensureProcessingCapability('converter', OFFICE_CONVERT_JOB_TYPE)

  input.reportProgress?.('Загружаю исходный файл...')
  const upload = await uploadProcessingFile(input.file)

  input.reportProgress?.('Запускаю конвертацию документа...')
  const createdJob = await createProcessingJob({
    uploadId: upload.id,
    jobType: OFFICE_CONVERT_JOB_TYPE,
    parameters: {
      targetExtension: input.targetExtension,
      maxWidth: input.maxWidth,
      maxHeight: input.maxHeight,
      quality: input.quality,
      backgroundColor: input.backgroundColor,
      presetLabel: input.presetLabel,
    },
  })
  input.onJobCreated?.(createdJob)

  const completedJob = await awaitProcessingJob(createdJob.id, {
    reportProgress: input.reportProgress,
    timeoutMessage:
      'Подготовка документа заняла слишком много времени. Попробуй другой формат результата или повтори позже.',
    onUpdate: input.onJobUpdate,
  })

  input.reportProgress?.('Загружаю готовый файл и предпросмотр...')
  return downloadServerOfficeConvertArtifacts(completedJob)
}

export async function runServerMediaConvert(
  input: RunServerMediaConvertInput,
): Promise<ServerMediaConvertResult> {
  input.reportProgress?.('Проверяю доступность конвертации медиа...')
  await ensureProcessingCapability('converter', MEDIA_CONVERT_JOB_TYPE)

  input.reportProgress?.('Загружаю исходный файл...')
  const upload = await uploadProcessingFile(input.file)

  input.reportProgress?.('Запускаю конвертацию медиа...')
  const createdJob = await createProcessingJob({
    uploadId: upload.id,
    jobType: MEDIA_CONVERT_JOB_TYPE,
    parameters: {
      targetExtension: input.targetExtension,
      videoCodec: input.videoCodec,
      audioCodec: input.audioCodec,
      maxWidth: input.maxWidth,
      maxHeight: input.maxHeight,
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
      'Обработка медиа заняла слишком много времени. Попробуй более компактный профиль или повтори позже.',
    onUpdate: input.onJobUpdate,
  })

  input.reportProgress?.('Загружаю готовый файл и предпросмотр...')
  return downloadServerMediaConvertArtifacts(completedJob)
}

export async function downloadServerOfficeConvertArtifacts(
  job: ProcessingJobResponse,
): Promise<ServerOfficeConvertResult> {
  const manifestArtifact = job.artifacts.find(
    (artifact) => artifact.kind === 'office-convert-manifest',
  )
  const resultArtifact = job.artifacts.find((artifact) => artifact.kind === 'office-convert-binary')
  const previewArtifact = job.artifacts.find(
    (artifact) => artifact.kind === 'office-convert-preview',
  )

  if (!manifestArtifact || !resultArtifact || !previewArtifact) {
    throw new Error('Конвертация документа завершилась без обязательных файлов результата.')
  }

  const [manifest, resultBlob, previewBlob] = await Promise.all([
    requestProcessingJson<ServerOfficeConvertManifest>(manifestArtifact.downloadPath),
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

export async function downloadServerMediaConvertArtifacts(
  job: ProcessingJobResponse,
): Promise<ServerMediaConvertResult> {
  const manifestArtifact = job.artifacts.find(
    (artifact) => artifact.kind === 'media-convert-manifest',
  )
  const resultArtifact = job.artifacts.find((artifact) => artifact.kind === 'media-convert-binary')
  const previewArtifact = job.artifacts.find(
    (artifact) => artifact.kind === 'media-convert-preview',
  )

  if (!manifestArtifact || !resultArtifact || !previewArtifact) {
    throw new Error('Конвертация медиа завершилась без обязательных файлов результата.')
  }

  const [manifest, resultBlob, previewBlob] = await Promise.all([
    requestProcessingJson<ServerMediaConvertManifest>(manifestArtifact.downloadPath),
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
