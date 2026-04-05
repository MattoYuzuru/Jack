export type ProcessingJobStatus = 'QUEUED' | 'RUNNING' | 'COMPLETED' | 'FAILED'
export type ProcessingProgressReporter = (message: string) => void

export interface ProcessingCapabilityJobType {
  jobType: string
  implemented: boolean
  detail?: string
  notes?: string
}

export interface ProcessingCapabilityScope {
  scope: string
  phase: string
  jobTypes: ProcessingCapabilityJobType[]
  notes?: string[]
}

export interface ProcessingUploadResponse {
  id: string
}

export interface ProcessingArtifact {
  id: string
  kind: string
  fileName: string
  mediaType: string
  sizeBytes: number
  createdAt: string
  downloadPath: string
}

export interface ProcessingJobResponse {
  id: string
  status: ProcessingJobStatus
  progressPercent: number
  message: string
  errorMessage: string | null
  artifacts: ProcessingArtifact[]
}

interface RunProcessingJobOptions {
  scope: 'viewer' | 'converter'
  file: File
  jobType: string
  parameters?: Record<string, unknown>
  reportProgress?: ProcessingProgressReporter
  maxAttempts?: number
  pollIntervalMs?: number
  uploadMessage?: string
  createMessage?: string
  timeoutMessage?: string
}

const DEFAULT_API_BASE_URL = 'http://localhost:8080'
const DEFAULT_MAX_ATTEMPTS = 300
const DEFAULT_POLL_INTERVAL_MS = 1_000

export async function ensureProcessingCapability(
  scope: RunProcessingJobOptions['scope'],
  jobType: string,
): Promise<ProcessingCapabilityJobType> {
  const capabilityScope = await requestProcessingJson<ProcessingCapabilityScope>(
    `/api/capabilities/${scope}`,
  )
  const capability = capabilityScope.jobTypes.find((entry) => entry.jobType === jobType)

  if (!capability?.implemented) {
    throw new Error(
      capability?.detail ||
        capability?.notes ||
        `Backend capability ${jobType} сейчас не активна для scope ${scope}.`,
    )
  }

  return capability
}

export async function uploadProcessingFile(file: File): Promise<ProcessingUploadResponse> {
  const formData = new FormData()
  formData.set('file', file, file.name)

  return requestProcessingJson<ProcessingUploadResponse>('/api/uploads', {
    method: 'POST',
    body: formData,
  })
}

export async function createProcessingJob(input: {
  uploadId: string
  jobType: string
  parameters?: Record<string, unknown>
}): Promise<ProcessingJobResponse> {
  return requestProcessingJson<ProcessingJobResponse>('/api/jobs', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(input),
  })
}

export async function awaitProcessingJob(
  jobId: string,
  options: {
    reportProgress?: ProcessingProgressReporter
    maxAttempts?: number
    pollIntervalMs?: number
    timeoutMessage?: string
  } = {},
): Promise<ProcessingJobResponse> {
  const {
    reportProgress,
    maxAttempts = DEFAULT_MAX_ATTEMPTS,
    pollIntervalMs = DEFAULT_POLL_INTERVAL_MS,
    timeoutMessage = 'Backend processing job не завершился в ожидаемое время.',
  } = options
  let lastMessage = ''

  for (let attempt = 0; attempt < maxAttempts; attempt += 1) {
    const job = await requestProcessingJson<ProcessingJobResponse>(`/api/jobs/${jobId}`)

    if (job.message && job.message !== lastMessage) {
      reportProgress?.(job.message)
      lastMessage = job.message
    }

    if (job.status === 'COMPLETED') {
      return job
    }

    if (job.status === 'FAILED') {
      throw new Error(
        job.errorMessage || job.message || 'Backend processing job завершился с ошибкой.',
      )
    }

    await sleep(pollIntervalMs)
  }

  throw new Error(timeoutMessage)
}

export async function runProcessingJob(
  options: RunProcessingJobOptions,
): Promise<ProcessingJobResponse> {
  const {
    scope,
    file,
    jobType,
    parameters,
    reportProgress,
    maxAttempts,
    pollIntervalMs,
    uploadMessage = 'Отправляю файл в backend processing pipeline...',
    createMessage = `Создаю backend ${jobType} job...`,
    timeoutMessage = `Backend ${jobType} job не завершился в ожидаемое время.`,
  } = options

  reportProgress?.(`Проверяю, что backend ${jobType} capability доступна для ${scope}...`)
  await ensureProcessingCapability(scope, jobType)

  reportProgress?.(uploadMessage)
  const upload = await uploadProcessingFile(file)

  reportProgress?.(createMessage)
  const job = await createProcessingJob({
    uploadId: upload.id,
    jobType,
    parameters,
  })

  return awaitProcessingJob(job.id, {
    reportProgress,
    maxAttempts,
    pollIntervalMs,
    timeoutMessage,
  })
}

export async function requestProcessingJson<T>(
  path: string,
  init: RequestInit = {},
): Promise<T> {
  const response = await processingFetch(path, init)

  if (!response.ok) {
    throw new Error(await resolveProcessingApiErrorMessage(response))
  }

  return (await response.json()) as T
}

export async function requestProcessingBlob(
  path: string,
  init: RequestInit = {},
): Promise<Blob> {
  const response = await processingFetch(path, init)

  if (!response.ok) {
    throw new Error(await resolveProcessingApiErrorMessage(response))
  }

  return response.blob()
}

async function processingFetch(path: string, init: RequestInit): Promise<Response> {
  try {
    return await fetch(resolveProcessingApiUrl(path), init)
  } catch {
    throw new Error(
      'Не удалось связаться с backend processing service. Проверь, что backend запущен и разрешает запросы с frontend origin.',
    )
  }
}

async function resolveProcessingApiErrorMessage(response: Response): Promise<string> {
  const contentType = response.headers.get('content-type') || ''

  if (contentType.includes('application/json')) {
    const payload = (await response.json().catch(() => null)) as Record<string, unknown> | null
    const messageCandidates = [
      payload?.detail,
      payload?.message,
      payload?.errorMessage,
      payload?.reason,
      payload?.title,
    ]
    const message = messageCandidates.find(
      (candidate): candidate is string =>
        typeof candidate === 'string' && candidate.trim().length > 0,
    )

    if (message) {
      return message
    }
  }

  const fallbackText = await response.text().catch(() => '')
  if (fallbackText.trim()) {
    return fallbackText.trim()
  }

  return `Backend processing request завершился с HTTP ${response.status}.`
}

function resolveProcessingApiUrl(path: string): string {
  return new URL(path, `${resolveProcessingApiBaseUrl()}/`).toString()
}

function resolveProcessingApiBaseUrl(): string {
  const configuredBaseUrl = import.meta.env.VITE_API_BASE_URL?.trim()

  if (configuredBaseUrl) {
    return configuredBaseUrl.replace(/\/+$/, '')
  }

  return DEFAULT_API_BASE_URL
}

function sleep(timeoutMs: number): Promise<void> {
  return new Promise((resolve) => {
    window.setTimeout(resolve, timeoutMs)
  })
}
