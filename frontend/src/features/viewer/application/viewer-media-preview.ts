import type { ViewerFormatDefinition } from '../domain/viewer-registry'
import {
  requestProcessingBlob,
  requestProcessingJson,
  runProcessingJob,
  type ProcessingJobResponse,
} from '../../processing/application/processing-client'
import type { ViewerAudioPreviewPayload } from './viewer-audio'
import type { ViewerVideoPreviewPayload } from './viewer-video'
import { buildAudioPreviewFromBlob } from './viewer-audio-preview'
import { buildVideoPreviewFromBlob } from './viewer-video-preview'

type ViewerMediaPreviewFamily = 'media' | 'audio'
type ViewerProgressReporter = (message: string) => void

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
  const completedJob = await runProcessingJob({
    scope: 'viewer',
    file,
    jobType: MEDIA_PREVIEW_JOB_TYPE,
    reportProgress,
    createMessage: 'Создаю backend MEDIA_PREVIEW job...',
    timeoutMessage:
      'Backend MEDIA_PREVIEW job не завершился в ожидаемое время. Попробуй файл меньшего размера или проверь backend logs.',
  })

  reportProgress?.('Загружаю media preview artifact и manifest с backend...')
  return downloadViewerMediaPreviewArtifacts(completedJob, expectedFamily)
}

async function downloadViewerMediaPreviewArtifacts(
  job: ProcessingJobResponse,
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
    requestProcessingJson<ViewerMediaPreviewManifest>(manifestArtifact.downloadPath),
    requestProcessingBlob(previewArtifact.downloadPath),
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

function deduplicateViewerWarnings(warnings: string[]): string[] {
  return Array.from(new Set(warnings.filter(Boolean)))
}
