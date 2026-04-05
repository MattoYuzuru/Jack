import {
  requestProcessingBlob,
  requestProcessingJson,
  runProcessingJob,
  type ProcessingJobResponse,
} from '../../processing/application/processing-client'

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
  manifest: ServerImageConvertManifest
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
}

const IMAGE_CONVERT_JOB_TYPE = 'IMAGE_CONVERT'

export async function runServerImageConvert(
  input: RunServerImageConvertInput,
): Promise<ServerImageConvertResult> {
  const completedJob = await runProcessingJob({
    scope: 'converter',
    file: input.file,
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
    reportProgress: input.reportProgress,
    createMessage: 'Создаю backend IMAGE_CONVERT job для heavy conversion scenario...',
    timeoutMessage:
      'Backend IMAGE_CONVERT job не завершился в ожидаемое время. Попробуй более компактный пресет или проверь backend logs.',
  })

  return downloadServerConvertArtifacts(completedJob)
}

async function downloadServerConvertArtifacts(
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
    manifest,
    resultBlob,
    previewBlob,
  }
}
