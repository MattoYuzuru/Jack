export type ProcessingJobStatus = 'QUEUED' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'CANCELLED'
export type ProcessingProgressReporter = (message: string) => void
export type ProcessingCapabilityScopeName =
  | 'viewer'
  | 'converter'
  | 'compression'
  | 'pdf-toolkit'
  | 'editor'
  | 'platform'

export interface ProcessingCapabilityJobType {
  jobType: string
  implemented: boolean
  detail?: string
  notes?: string
}

export interface ProcessingCapabilityScope {
  scope: ProcessingCapabilityScopeName
  phase: string
  jobTypes: ProcessingCapabilityJobType[]
  notes?: string[]
  viewerMatrix?: {
    acceptAttribute: string
    formats: unknown[]
  } | null
  converterMatrix?: {
    acceptAttribute: string
    sourceFormats: unknown[]
    targetFormats: unknown[]
    scenarios: unknown[]
    presets: unknown[]
  } | null
  compressionMatrix?: {
    acceptAttribute: string
    sourceFormats: unknown[]
    targetFormats: unknown[]
    modes: unknown[]
  } | null
  pdfToolkitMatrix?: {
    acceptAttribute: string
    importAcceptAttribute: string
    directSourceFormats: unknown[]
    importSourceFormats: unknown[]
    operations: unknown[]
  } | null
  editorMatrix?: {
    acceptAttribute: string
    formats: unknown[]
  } | null
  platformMatrix?: ProcessingPlatformCapabilityMatrix | null
}

export interface ProcessingPlatformCapabilityMatrix {
  modules: ProcessingPlatformModuleCapability[]
}

export interface ProcessingPlatformModuleCapability {
  id: string
  label: string
  summary: string
  detail: string
  statusLabel: string
  accents: string[]
  reusedDomains: string[]
  reusedJobTypes: string[]
  nextSlices: string[]
  foundationReady: boolean
  availabilityDetail?: string | null
}

export interface ProcessingUploadResponse {
  id: string
  sha256?: string
}

export interface ProcessingArtifact {
  id: string
  kind: string
  fileName: string
  mediaType: string
  sizeBytes: number
  sha256?: string
  createdAt: string
  expiresAt?: string
  downloadPath: string
}

export interface ProcessingJobResponse {
  id: string
  uploadId: string
  jobType: string
  status: ProcessingJobStatus
  progressPercent: number
  message: string
  errorCode?: string | null
  errorMessage: string | null
  correlationId?: string
  createdAt: string
  startedAt: string | null
  completedAt: string | null
  expiresAt?: string
  policyVersion?: string
  artifacts: ProcessingArtifact[]
}

export interface RunProcessingJobOptions {
  scope: Exclude<ProcessingCapabilityScopeName, 'platform'>
  file: File
  jobType: string
  parameters?: Record<string, unknown>
  reportProgress?: ProcessingProgressReporter
  maxAttempts?: number
  pollIntervalMs?: number
  uploadMessage?: string
  createMessage?: string
  timeoutMessage?: string
  onJobCreated?: (job: ProcessingJobResponse) => void
  onJobUpdate?: (job: ProcessingJobResponse) => void
  /**
   * Останавливает сетевое ожидание и отменяет уже созданную backend-задачу.
   * Это важно для экранов с быстрой заменой файла: stale-задача не должна
   * продолжать занимать лимит processing platform после ухода пользователя.
   */
  signal?: AbortSignal
}

export interface AwaitProcessingJobOptions {
  reportProgress?: ProcessingProgressReporter
  maxAttempts?: number
  pollIntervalMs?: number
  timeoutMessage?: string
  onUpdate?: (job: ProcessingJobResponse) => void
  signal?: AbortSignal
  cancelOnAbort?: boolean
  cancelOnTimeout?: boolean
}

const DEFAULT_API_BASE_URL = 'http://localhost:8080'
const DEFAULT_MAX_ATTEMPTS = 300
const DEFAULT_POLL_INTERVAL_MS = 1_000
const capabilityScopeCache = new Map<
  ProcessingCapabilityScopeName,
  Promise<ProcessingCapabilityScope>
>()

export class ProcessingJobCancelledError extends Error {
  readonly job: ProcessingJobResponse

  constructor(job: ProcessingJobResponse) {
    super(job.errorMessage || job.message || 'Операция была остановлена до завершения.')
    this.name = 'ProcessingJobCancelledError'
    this.job = job
  }
}

export class ProcessingJobAbortedError extends Error {
  constructor() {
    super('Операция была остановлена до завершения.')
    this.name = 'ProcessingJobAbortedError'
  }
}

export function resetProcessingCapabilityScopeCache(): void {
  capabilityScopeCache.clear()
}

export async function getProcessingCapabilityScope(
  scope: ProcessingCapabilityScopeName,
): Promise<ProcessingCapabilityScope> {
  const cachedScope = capabilityScopeCache.get(scope)
  if (cachedScope) {
    return cachedScope
  }

  const scopeRequest = requestProcessingJson<ProcessingCapabilityScope>(
    `/api/capabilities/${scope}`,
  )
  capabilityScopeCache.set(scope, scopeRequest)

  try {
    return await scopeRequest
  } catch (error) {
    capabilityScopeCache.delete(scope)
    throw error
  }
}

export async function ensureProcessingCapability(
  scope: Exclude<ProcessingCapabilityScopeName, 'platform'>,
  jobType: string,
): Promise<ProcessingCapabilityJobType> {
  const capabilityScope = await getProcessingCapabilityScope(scope)
  const capability = capabilityScope.jobTypes.find((entry) => entry.jobType === jobType)

  if (!capability?.implemented) {
    throw new Error(
      capability?.detail ||
        capability?.notes ||
        'Этот сценарий сейчас недоступен. Попробуй позже или выбери другой формат.',
    )
  }

  return capability
}

export async function uploadProcessingFile(
  file: File,
  init: Pick<RequestInit, 'signal'> = {},
): Promise<ProcessingUploadResponse> {
  const formData = new FormData()
  formData.set('file', file, file.name)

  return requestProcessingJson<ProcessingUploadResponse>('/api/uploads', {
    method: 'POST',
    body: formData,
    ...init,
  })
}

export async function getPlatformCapabilityMatrix(): Promise<ProcessingPlatformCapabilityMatrix> {
  const scope = await getProcessingCapabilityScope('platform')

  if (!scope.platformMatrix) {
    throw new Error('Не удалось загрузить конфигурацию модулей Jack.')
  }

  return scope.platformMatrix
}

export async function createProcessingJob(
  input: {
    uploadId: string
    jobType: string
    parameters?: Record<string, unknown>
  },
  init: Pick<RequestInit, 'signal'> = {},
): Promise<ProcessingJobResponse> {
  const sanitizedParameters = sanitizeProcessingParameters(input.parameters)

  return requestProcessingJson<ProcessingJobResponse>('/api/jobs', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      ...input,
      ...(sanitizedParameters ? { parameters: sanitizedParameters } : {}),
    }),
    ...init,
  })
}

export async function getProcessingJob(
  jobId: string,
  init: Pick<RequestInit, 'signal'> = {},
): Promise<ProcessingJobResponse> {
  return requestProcessingJson<ProcessingJobResponse>(`/api/jobs/${jobId}`, init)
}

export async function cancelProcessingJob(jobId: string): Promise<ProcessingJobResponse> {
  return requestProcessingJson<ProcessingJobResponse>(`/api/jobs/${jobId}`, {
    method: 'DELETE',
  })
}

export async function awaitProcessingJob(
  jobId: string,
  options: AwaitProcessingJobOptions = {},
): Promise<ProcessingJobResponse> {
  const {
    reportProgress,
    maxAttempts = DEFAULT_MAX_ATTEMPTS,
    pollIntervalMs = DEFAULT_POLL_INTERVAL_MS,
    timeoutMessage = 'Операция заняла больше времени, чем ожидалось.',
    onUpdate,
    signal,
    cancelOnAbort = true,
    cancelOnTimeout = true,
  } = options
  let lastMessage = ''

  try {
    for (let attempt = 0; attempt < maxAttempts; attempt += 1) {
      throwIfProcessingAborted(signal)
      const job = await getProcessingJob(jobId, { signal })
      onUpdate?.(job)

      if (job.message && job.message !== lastMessage) {
        reportProgress?.(job.message)
        lastMessage = job.message
      }

      if (job.status === 'COMPLETED') {
        return job
      }

      if (job.status === 'FAILED') {
        throw new Error(job.errorMessage || job.message || 'Операция завершилась с ошибкой.')
      }

      if (job.status === 'CANCELLED') {
        throw new ProcessingJobCancelledError(job)
      }

      await sleep(pollIntervalMs, signal)
    }
  } catch (error) {
    if (isProcessingAbort(error, signal)) {
      if (cancelOnAbort) {
        void cancelProcessingJob(jobId).catch(() => undefined)
      }

      throw new ProcessingJobAbortedError()
    }

    throw error
  }

  if (cancelOnTimeout) {
    await cancelProcessingJob(jobId).catch(() => undefined)
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
    uploadMessage = 'Загружаю файл...',
    createMessage = 'Запускаю обработку...',
    timeoutMessage = 'Обработка заняла больше времени, чем ожидалось.',
    onJobCreated,
    onJobUpdate,
    signal,
  } = options

  throwIfProcessingAborted(signal)
  reportProgress?.('Проверяю доступность обработки...')
  await ensureProcessingCapability(scope, jobType)
  throwIfProcessingAborted(signal)

  reportProgress?.(uploadMessage)
  const upload = await uploadProcessingFile(file, { signal })

  reportProgress?.(createMessage)
  const job = await createProcessingJob(
    {
      uploadId: upload.id,
      jobType,
      parameters,
    },
    { signal },
  )
  onJobCreated?.(job)

  try {
    return await awaitProcessingJob(job.id, {
      reportProgress,
      maxAttempts,
      pollIntervalMs,
      timeoutMessage,
      onUpdate: onJobUpdate,
      signal,
      // runProcessingJob сам отменяет задачу в catch, чтобы DELETE ушёл
      // ровно один раз и при timeout, и при отмене маршрута.
      cancelOnAbort: false,
    })
  } catch (error) {
    if (!(error instanceof ProcessingJobCancelledError)) {
      void cancelProcessingJob(job.id).catch(() => undefined)
    }

    throw error
  }
}

export async function requestProcessingJson<T>(path: string, init: RequestInit = {}): Promise<T> {
  const response = await processingFetch(path, init)

  if (!response.ok) {
    throw new Error(await resolveProcessingApiErrorMessage(response))
  }

  return (await response.json()) as T
}

export async function requestProcessingBlob(path: string, init: RequestInit = {}): Promise<Blob> {
  const response = await processingFetch(path, init)

  if (!response.ok) {
    throw new Error(await resolveProcessingApiErrorMessage(response))
  }

  return response.blob()
}

async function processingFetch(path: string, init: RequestInit): Promise<Response> {
  throwIfProcessingAborted(init.signal)

  const method = (init.method || 'GET').toUpperCase()
  const headers = new Headers(init.headers)
  if (['POST', 'PUT', 'PATCH', 'DELETE'].includes(method)) {
    headers.set('X-Jack-Request', 'processing')
  }

  try {
    return await fetch(resolveProcessingApiUrl(path), {
      credentials: 'include',
      ...init,
      headers,
    })
  } catch (error) {
    if (isProcessingAbort(error, init.signal)) {
      throw new ProcessingJobAbortedError()
    }

    throw new Error(
      'Не удалось связаться с сервисом обработки. Проверь, что Jack запущен и доступен.',
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

  return `Сервис обработки вернул HTTP ${response.status}.`
}

function resolveProcessingApiUrl(path: string): string {
  const baseUrl = resolveProcessingApiBaseUrl()

  if (isAbsoluteProcessingApiBaseUrl(baseUrl)) {
    return new URL(path, `${baseUrl}/`).toString()
  }

  const normalizedBasePath = normalizeRelativeProcessingApiBasePath(baseUrl)
  const normalizedPath = path.startsWith('/') ? path : `/${path}`

  // В production база часто задаётся как "/api", а сами вызовы уже содержат тот же префикс.
  // Здесь важно не дублировать его и всегда резолвить URL от origin текущего сайта.
  if (
    normalizedPath === normalizedBasePath ||
    normalizedPath.startsWith(`${normalizedBasePath}/`)
  ) {
    return new URL(normalizedPath, window.location.origin).toString()
  }

  return new URL(`${normalizedBasePath}${normalizedPath}`, window.location.origin).toString()
}

function resolveProcessingApiBaseUrl(): string {
  const configuredBaseUrl = import.meta.env.VITE_API_BASE_URL?.trim()

  if (configuredBaseUrl) {
    return configuredBaseUrl.replace(/\/+$/, '')
  }

  return DEFAULT_API_BASE_URL
}

function isAbsoluteProcessingApiBaseUrl(baseUrl: string): boolean {
  return /^[a-z][a-z\d+.-]*:/i.test(baseUrl)
}

function normalizeRelativeProcessingApiBasePath(baseUrl: string): string {
  const trimmedBaseUrl = baseUrl.replace(/\/+$/, '')

  if (!trimmedBaseUrl) {
    return ''
  }

  return trimmedBaseUrl.startsWith('/') ? trimmedBaseUrl : `/${trimmedBaseUrl}`
}

function sleep(timeoutMs: number, signal?: AbortSignal): Promise<void> {
  return new Promise((resolve, reject) => {
    const onAbort = () => {
      window.clearTimeout(timeoutId)
      signal?.removeEventListener('abort', onAbort)
      reject(new ProcessingJobAbortedError())
    }
    const timeoutId = window.setTimeout(() => {
      signal?.removeEventListener('abort', onAbort)
      resolve()
    }, timeoutMs)

    if (signal?.aborted) {
      onAbort()
      return
    }

    signal?.addEventListener('abort', onAbort, { once: true })
  })
}

function throwIfProcessingAborted(signal?: AbortSignal | null): void {
  if (signal?.aborted) {
    throw new ProcessingJobAbortedError()
  }
}

function isProcessingAbort(error: unknown, signal?: AbortSignal | null): boolean {
  return (
    signal?.aborted === true ||
    error instanceof ProcessingJobAbortedError ||
    (error instanceof DOMException && error.name === 'AbortError')
  )
}

function sanitizeProcessingParameters(
  parameters: Record<string, unknown> | undefined,
): Record<string, unknown> | undefined {
  if (!parameters) {
    return undefined
  }

  const sanitized = Object.fromEntries(
    Object.entries(parameters).filter(([, value]) => value !== null && value !== undefined),
  )

  return Object.keys(sanitized).length ? sanitized : undefined
}
