import type { ViewerFormatDefinition } from '../domain/viewer-registry'
import { formatViewerVideoDuration, type ViewerVideoPreviewPayload } from './viewer-video'
import {
  estimateViewerVideoBitrateBitsPerSecond,
  formatViewerAspectRatio,
  formatViewerVideoBitrate,
  resolveViewerVideoOrientation,
} from './viewer-video-tools'

interface NativeVideoMetadata {
  durationSeconds: number
  width: number
  height: number
}

interface BuildVideoPreviewOptions {
  previewLabel?: string
  warnings?: string[]
  extraSummary?: ViewerVideoPreviewPayload['summary']
  playbackPathLabel?: string
  metadataMimeType?: string
}

export async function buildNativeVideoPreview(
  file: File,
  format: ViewerFormatDefinition,
): Promise<ViewerVideoPreviewPayload> {
  const warnings: string[] = []

  if (format.extension === 'mov') {
    warnings.push(
      'Воспроизведение MOV зависит от поддержки кодека в браузере, поэтому поведение может отличаться на разных устройствах.',
    )
  }

  return buildVideoPreviewFromBlob(file, format, {
    warnings,
    playbackPathLabel: 'Браузерное воспроизведение',
    metadataMimeType: file.type,
  })
}

export async function buildVideoPreviewFromBlob(
  blob: Blob,
  format: ViewerFormatDefinition,
  options: BuildVideoPreviewOptions = {},
): Promise<ViewerVideoPreviewPayload> {
  const objectUrl = URL.createObjectURL(blob)

  try {
    const metadata = await inspectNativeVideo(objectUrl)
    const estimatedBitrate = estimateViewerVideoBitrateBitsPerSecond(
      blob.size,
      metadata.durationSeconds,
    )

    return {
      summary: [
        { label: 'Тип видео', value: format.label },
        { label: 'Длительность', value: formatViewerVideoDuration(metadata.durationSeconds) },
        { label: 'Кадр', value: `${metadata.width} x ${metadata.height}` },
        {
          label: 'Соотношение сторон',
          value: formatViewerAspectRatio(metadata.width, metadata.height),
        },
        {
          label: 'Ориентация',
          value: resolveViewerVideoOrientation(metadata.width, metadata.height),
        },
        { label: 'Битрейт', value: formatViewerVideoBitrate(estimatedBitrate) },
        {
          label: 'Режим воспроизведения',
          value: options.playbackPathLabel ?? 'Браузерное воспроизведение',
        },
        ...(options.extraSummary ?? []),
      ],
      warnings: options.warnings ?? [],
      layout: {
        mode: 'native',
        objectUrl,
        durationSeconds: metadata.durationSeconds,
        width: metadata.width,
        height: metadata.height,
        metadata: {
          mimeType: options.metadataMimeType || blob.type || 'Не определён',
          aspectRatio: formatViewerAspectRatio(metadata.width, metadata.height),
          orientation: resolveViewerVideoOrientation(metadata.width, metadata.height),
          estimatedBitrateBitsPerSecond: estimatedBitrate,
          sizeBytes: blob.size,
        },
      },
      previewLabel: options.previewLabel ?? format.statusLabel,
    }
  } catch (error) {
    URL.revokeObjectURL(objectUrl)
    throw error
  }
}

function inspectNativeVideo(objectUrl: string): Promise<NativeVideoMetadata> {
  return new Promise((resolve, reject) => {
    const video = document.createElement('video')

    video.preload = 'metadata'
    video.playsInline = true
    video.muted = true

    const cleanup = () => {
      video.removeAttribute('src')
      video.load()
    }

    video.onloadedmetadata = () => {
      resolve({
        durationSeconds: Number.isFinite(video.duration) ? video.duration : 0,
        width: video.videoWidth,
        height: video.videoHeight,
      })
      cleanup()
    }

    video.onerror = () => {
      reject(new Error('Не удалось прочитать данные видеофайла для просмотра.'))
      cleanup()
    }

    video.src = objectUrl
  })
}
