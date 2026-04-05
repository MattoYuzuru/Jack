import type { ViewerFormatDefinition } from '../domain/viewer-registry'
import type { ViewerAudioPreviewPayload } from './viewer-audio'
import type { ViewerVideoPreviewPayload } from './viewer-video'
import { buildAudioPreviewFromBlob } from './viewer-audio-preview'
import { buildVideoPreviewFromBlob } from './viewer-video-preview'

type ViewerMediaPreviewFamily = 'media' | 'audio'
type ViewerProcessingJobStatus = 'QUEUED' | 'RUNNING' | 'COMPLETED' | 'FAILED'
type ViewerProgressReporter = (message: string) => void

interface ViewerCapabilityJobType {
  jobType: string
  implemented: boolean
  notes: string
}

interface ViewerCapabilityScope {
  scope: string
  phase: string
  jobTypes: ViewerCapabilityJobType[]
}

interface ViewerUploadResponse {
  id: string
}

interface ViewerProcessingArtifact {
  id: string
  kind: string
  fileName: string
  mediaType: string
  sizeBytes: number
  createdAt: string
  downloadPath: string
}

interface ViewerProcessingJobResponse {
  id: string
  status: ViewerProcessingJobStatus
  progressPercent: number
  message: string
  errorMessage: string | null
  artifacts: ViewerProcessingArtifact[]
}

interface ViewerMediaPreviewProbe {
  durationSeconds: number | null
  codecName: string | null
  width: number | null
  height: number | null
  sampleRate: number | null
  channelCount: number | null
}

interface ViewerMediaPreviewManifest {
  uploadId: string
  originalFileName: string
  family: ViewerMediaPreviewFamily
  probe: ViewerMediaPreviewProbe
  runtimeLabel: string
  previewMediaType: string
  generatedAt: string
  warnings: string[]
}

interface ViewerServerMediaPreviewArtifacts {
  manifest: ViewerMediaPreviewManifest
  previewBlob: Blob
}

const MEDIA_PREVIEW_JOB_TYPE = 'MEDIA_PREVIEW'
const MEDIA_PREVIEW_MAX_ATTEMPTS = 300
const MEDIA_PREVIEW_POLL_INTERVAL_MS = 1_000
const DEFAULT_API_BASE_URL = 'http://localhost:8080'

export async function buildLegacyVideoPreview(
  file: File,
  format: ViewerFormatDefinition,
  reportProgress?: ViewerProgressReporter,
): Promise<ViewerVideoPreviewPayload> {
  const mediaPreview = await loadViewerServerMediaPreview(file, 'media', reportProgress)

  return buildVideoPreviewFromBlob(mediaPreview.previewBlob, format, {
    previewLabel: 'Server media preview',
    playbackPathLabel: `Backend MEDIA_PREVIEW · ${mediaPreview.manifest.runtimeLabel}`,
    metadataMimeType:
      mediaPreview.manifest.previewMediaType || mediaPreview.previewBlob.type || file.type,
    warnings: deduplicateViewerWarnings([
      'Legacy video preview собирается через backend MEDIA_PREVIEW job: браузер больше не тянет локальный ffmpeg runtime для этого контейнера.',
      ...mediaPreview.manifest.warnings,
    ]),
    extraSummary: [
      { label: 'Runtime Container', value: mediaPreview.manifest.runtimeLabel },
      {
        label: 'Source Codec',
        value: mediaPreview.manifest.probe.codecName ?? 'Не удалось извлечь через ffprobe',
      },
      {
        label: 'Source Frame',
        value:
          mediaPreview.manifest.probe.width && mediaPreview.manifest.probe.height
            ? `${mediaPreview.manifest.probe.width} x ${mediaPreview.manifest.probe.height}`
            : 'Не удалось извлечь через ffprobe',
      },
    ],
  })
}

export async function buildLegacyAudioPreview(
  file: File,
  format: ViewerFormatDefinition,
  reportProgress?: ViewerProgressReporter,
): Promise<ViewerAudioPreviewPayload> {
  const mediaPreview = await loadViewerServerMediaPreview(file, 'audio', reportProgress)

  return buildAudioPreviewFromBlob(mediaPreview.previewBlob, format, {
    previewLabel: 'Server audio preview',
    playbackPathLabel: `Backend MEDIA_PREVIEW · ${mediaPreview.manifest.runtimeLabel}`,
    metadataMimeType:
      mediaPreview.manifest.previewMediaType || mediaPreview.previewBlob.type || file.type,
    metadataSource: file,
    warnings: deduplicateViewerWarnings([
      'Legacy audio preview собирается через backend MEDIA_PREVIEW job: браузер получает уже готовый playback artifact вместо локального transcode bridge.',
      ...mediaPreview.manifest.warnings,
    ]),
    extraSummary: [
      { label: 'Runtime Container', value: mediaPreview.manifest.runtimeLabel },
      {
        label: 'Source Codec',
        value: mediaPreview.manifest.probe.codecName ?? 'Не удалось извлечь через ffprobe',
      },
      {
        label: 'Source Sample Rate',
        value:
          mediaPreview.manifest.probe.sampleRate != null
            ? `${mediaPreview.manifest.probe.sampleRate} Hz`
            : 'Не удалось извлечь через ffprobe',
      },
      {
        label: 'Source Channels',
        value:
          mediaPreview.manifest.probe.channelCount != null
            ? String(mediaPreview.manifest.probe.channelCount)
            : 'Не удалось извлечь через ffprobe',
      },
    ],
  })
}

async function loadViewerServerMediaPreview(
  file: File,
  expectedFamily: ViewerMediaPreviewFamily,
  reportProgress?: ViewerProgressReporter,
): Promise<ViewerServerMediaPreviewArtifacts> {
  reportProgress?.('Проверяю, что backend media capability доступна для viewer...')
  await ensureViewerMediaPreviewCapability()

  reportProgress?.('Отправляю файл в backend processing pipeline...')
  const formData = new FormData()
  formData.set('file', file, file.name)

  const upload = await requestJson<ViewerUploadResponse>('/api/uploads', {
    method: 'POST',
    body: formData,
  })

  reportProgress?.('Создаю backend MEDIA_PREVIEW job...')
  const job = await requestJson<ViewerProcessingJobResponse>('/api/jobs', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      uploadId: upload.id,
      jobType: MEDIA_PREVIEW_JOB_TYPE,
    }),
  })

  const completedJob = await awaitViewerMediaPreviewJob(job.id, reportProgress)
  reportProgress?.('Загружаю media preview artifact и manifest с backend...')
  return downloadViewerMediaPreviewArtifacts(completedJob, expectedFamily)
}

async function ensureViewerMediaPreviewCapability(): Promise<void> {
  const capabilityScope = await requestJson<ViewerCapabilityScope>('/api/capabilities/viewer')
  const capability = capabilityScope.jobTypes.find(
    (jobType) => jobType.jobType === MEDIA_PREVIEW_JOB_TYPE,
  )

  if (!capability?.implemented) {
    throw new Error(
      capability?.notes ||
        'Backend MEDIA_PREVIEW capability сейчас не активна. Проверь доступность ffmpeg/ffprobe на сервере.',
    )
  }
}

async function awaitViewerMediaPreviewJob(
  jobId: string,
  reportProgress?: ViewerProgressReporter,
): Promise<ViewerProcessingJobResponse> {
  let lastMessage = ''

  for (let attempt = 0; attempt < MEDIA_PREVIEW_MAX_ATTEMPTS; attempt += 1) {
    const job = await requestJson<ViewerProcessingJobResponse>(`/api/jobs/${jobId}`)

    if (job.message && job.message !== lastMessage) {
      reportProgress?.(job.message)
      lastMessage = job.message
    }

    if (job.status === 'COMPLETED') {
      return job
    }

    if (job.status === 'FAILED') {
      throw new Error(
        job.errorMessage ||
          job.message ||
          'Backend MEDIA_PREVIEW job завершился с ошибкой до сборки preview artifact.',
      )
    }

    await sleep(MEDIA_PREVIEW_POLL_INTERVAL_MS)
  }

  throw new Error(
    'Backend MEDIA_PREVIEW job не завершился в ожидаемое время. Попробуй файл меньшего размера или проверь backend logs.',
  )
}

async function downloadViewerMediaPreviewArtifacts(
  job: ViewerProcessingJobResponse,
  expectedFamily: ViewerMediaPreviewFamily,
): Promise<ViewerServerMediaPreviewArtifacts> {
  const manifestArtifact = job.artifacts.find(
    (artifact) => artifact.kind === 'media-preview-manifest',
  )
  const previewArtifact = job.artifacts.find((artifact) => artifact.kind === 'media-preview-binary')

  if (!manifestArtifact || !previewArtifact) {
    throw new Error('Backend MEDIA_PREVIEW job завершился без обязательных artifacts.')
  }

  const [manifest, previewBlob] = await Promise.all([
    requestJson<ViewerMediaPreviewManifest>(manifestArtifact.downloadPath),
    requestBlob(previewArtifact.downloadPath),
  ])

  if (manifest.family !== expectedFamily) {
    throw new Error(
      `Backend MEDIA_PREVIEW вернул ${manifest.family} artifact для ${expectedFamily} preview path.`,
    )
  }

  return {
    manifest,
    previewBlob,
  }
}

async function requestJson<T>(path: string, init: RequestInit = {}): Promise<T> {
  const response = await viewerFetch(path, init)

  if (!response.ok) {
    throw new Error(await resolveViewerApiErrorMessage(response))
  }

  return (await response.json()) as T
}

async function requestBlob(path: string, init: RequestInit = {}): Promise<Blob> {
  const response = await viewerFetch(path, init)

  if (!response.ok) {
    throw new Error(await resolveViewerApiErrorMessage(response))
  }

  return response.blob()
}

async function viewerFetch(path: string, init: RequestInit): Promise<Response> {
  try {
    return await fetch(resolveViewerApiUrl(path), init)
  } catch {
    throw new Error(
      'Не удалось связаться с backend media preview service. Проверь, что backend запущен и разрешает запросы с frontend origin.',
    )
  }
}

async function resolveViewerApiErrorMessage(response: Response): Promise<string> {
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

  return `Backend media preview request завершился с HTTP ${response.status}.`
}

function resolveViewerApiUrl(path: string): string {
  return new URL(path, `${resolveViewerApiBaseUrl()}/`).toString()
}

function resolveViewerApiBaseUrl(): string {
  const configuredBaseUrl = import.meta.env.VITE_API_BASE_URL?.trim()

  if (configuredBaseUrl) {
    return configuredBaseUrl.replace(/\/+$/, '')
  }

  return DEFAULT_API_BASE_URL
}

function deduplicateViewerWarnings(warnings: string[]): string[] {
  return Array.from(new Set(warnings.filter(Boolean)))
}

function sleep(timeoutMs: number): Promise<void> {
  return new Promise((resolve) => {
    window.setTimeout(resolve, timeoutMs)
  })
}
