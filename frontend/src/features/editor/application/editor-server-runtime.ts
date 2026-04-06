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

export interface EditorFact {
  label: string
  value: string
}

export interface EditorIssue {
  severity: 'error' | 'warning' | 'info'
  code: string
  message: string
  line: number | null
  column: number | null
  hint: string | null
}

export interface EditorOutlineItem {
  id: string
  label: string
  depth: number
  kind: string
}

export interface ServerEditorManifest {
  uploadId: string
  originalFileName: string
  formatId: string
  formatLabel: string
  syntaxMode: string
  previewMode: string
  runtimeLabel: string
  summary: EditorFact[]
  issues: EditorIssue[]
  outline: EditorOutlineItem[]
  suggestions: string[]
  readyFileName: string
  readyMediaType: string
  plainTextFileName: string
  plainTextMediaType: string
  generatedAt: string
}

export interface ServerEditorResult {
  job: ProcessingJobResponse
  manifestArtifact: ProcessingArtifact
  readyArtifact: ProcessingArtifact
  plainTextArtifact: ProcessingArtifact
  manifest: ServerEditorManifest
  readyBlob: Blob
  plainTextBlob: Blob
}

export interface RunServerEditorInput {
  file: File
  formatId: string
  reportProgress?: (message: string) => void
  onJobCreated?: (job: ProcessingJobResponse) => void
  onJobUpdate?: (job: ProcessingJobResponse) => void
}

const EDITOR_PROCESS_JOB_TYPE = 'EDITOR_PROCESS'

export async function runServerEditorProcess(
  input: RunServerEditorInput,
): Promise<ServerEditorResult> {
  input.reportProgress?.('Проверяю доступность обработки документа...')
  await ensureProcessingCapability('editor', EDITOR_PROCESS_JOB_TYPE)

  input.reportProgress?.('Отправляю текущий текст на проверку...')
  const upload = await uploadProcessingFile(input.file)

  input.reportProgress?.('Запускаю проверку и подготовку файлов...')
  const createdJob = await createProcessingJob({
    uploadId: upload.id,
    jobType: EDITOR_PROCESS_JOB_TYPE,
    parameters: {
      formatId: input.formatId,
    },
  })
  input.onJobCreated?.(createdJob)

  const completedJob = await awaitProcessingJob(createdJob.id, {
    reportProgress: input.reportProgress,
    timeoutMessage:
      'Проверка заняла слишком много времени. Попробуй документ поменьше или повтори запуск позже.',
    onUpdate: input.onJobUpdate,
  })

  input.reportProgress?.('Загружаю подготовленные файлы...')
  return downloadServerEditorArtifacts(completedJob)
}

export async function downloadServerEditorArtifacts(
  job: ProcessingJobResponse,
): Promise<ServerEditorResult> {
  const manifestArtifact = job.artifacts.find((artifact) => artifact.kind === 'editor-manifest')
  const readyArtifact = job.artifacts.find((artifact) => artifact.kind === 'editor-export-ready')
  const plainTextArtifact = job.artifacts.find(
    (artifact) => artifact.kind === 'editor-export-plain-text',
  )

  if (!manifestArtifact || !readyArtifact || !plainTextArtifact) {
    throw new Error('Проверка завершилась без обязательных файлов результата.')
  }

  const [manifest, readyBlob, plainTextBlob] = await Promise.all([
    requestProcessingJson<ServerEditorManifest>(manifestArtifact.downloadPath),
    requestProcessingBlob(readyArtifact.downloadPath),
    requestProcessingBlob(plainTextArtifact.downloadPath),
  ])

  return {
    job,
    manifestArtifact,
    readyArtifact,
    plainTextArtifact,
    manifest,
    readyBlob,
    plainTextBlob,
  }
}
