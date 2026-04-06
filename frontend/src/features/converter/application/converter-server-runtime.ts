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
  input.reportProgress?.('Проверяю backend IMAGE_CONVERT capability для converter route...')
  await ensureProcessingCapability('converter', IMAGE_CONVERT_JOB_TYPE)

  input.reportProgress?.('Отправляю source в backend processing storage...')
  const upload = await uploadProcessingFile(input.file)

  input.reportProgress?.('Создаю backend IMAGE_CONVERT job для backend-first conversion...')
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
      'Backend IMAGE_CONVERT job не завершился в ожидаемое время. Попробуй более компактный пресет или проверь backend logs.',
    onUpdate: input.onJobUpdate,
  })

  input.reportProgress?.('Загружаю backend result/preview artifacts...')
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
    throw new Error('Backend IMAGE_CONVERT job завершился без обязательных convert artifacts.')
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
  input.reportProgress?.('Проверяю backend OFFICE_CONVERT capability для office/pdf route...')
  await ensureProcessingCapability('converter', OFFICE_CONVERT_JOB_TYPE)

  input.reportProgress?.('Отправляю source в backend processing storage...')
  const upload = await uploadProcessingFile(input.file)

  input.reportProgress?.('Создаю backend OFFICE_CONVERT job для office/pdf conversion...')
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
      'Backend OFFICE_CONVERT job не завершился в ожидаемое время. Проверь backend logs или попробуй менее тяжёлый export target.',
    onUpdate: input.onJobUpdate,
  })

  input.reportProgress?.('Загружаю backend office result/preview artifacts...')
  return downloadServerOfficeConvertArtifacts(completedJob)
}

export async function runServerMediaConvert(
  input: RunServerMediaConvertInput,
): Promise<ServerMediaConvertResult> {
  input.reportProgress?.('Проверяю backend MEDIA_CONVERT capability для video/audio route...')
  await ensureProcessingCapability('converter', MEDIA_CONVERT_JOB_TYPE)

  input.reportProgress?.('Отправляю source в backend processing storage...')
  const upload = await uploadProcessingFile(input.file)

  input.reportProgress?.('Создаю backend MEDIA_CONVERT job для container/codec transcode...')
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
      'Backend MEDIA_CONVERT job не завершился в ожидаемое время. Проверь ffmpeg runtime или попробуй более компактный delivery profile.',
    onUpdate: input.onJobUpdate,
  })

  input.reportProgress?.('Загружаю backend media result/preview artifacts...')
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
    throw new Error('Backend OFFICE_CONVERT job завершился без обязательных convert artifacts.')
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
    throw new Error('Backend MEDIA_CONVERT job завершился без обязательных convert artifacts.')
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
