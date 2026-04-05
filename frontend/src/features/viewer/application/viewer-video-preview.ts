import type { ViewerFormatDefinition } from '../domain/viewer-registry'
import { formatViewerVideoDuration, type ViewerVideoPreviewPayload } from './viewer-video'

interface NativeVideoMetadata {
  durationSeconds: number
  width: number
  height: number
}

export async function buildNativeVideoPreview(
  file: File,
  format: ViewerFormatDefinition,
): Promise<ViewerVideoPreviewPayload> {
  const objectUrl = URL.createObjectURL(file)

  try {
    const metadata = await inspectNativeVideo(objectUrl)
    const warnings: string[] = []

    if (format.extension === 'mov') {
      warnings.push(
        'MOV preview идёт через browser-native path и зависит от codec support в текущем браузере: metadata и playback могут отличаться для разных роликов.',
      )
    }

    return {
      summary: [
        { label: 'Тип видео', value: format.label },
        { label: 'Длительность', value: formatViewerVideoDuration(metadata.durationSeconds) },
        { label: 'Кадр', value: `${metadata.width} x ${metadata.height}` },
        { label: 'Playback path', value: 'Browser native video' },
      ],
      warnings,
      layout: {
        mode: 'native',
        objectUrl,
        durationSeconds: metadata.durationSeconds,
        width: metadata.width,
        height: metadata.height,
      },
      previewLabel: format.statusLabel,
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
      reject(new Error('Не удалось прочитать metadata из видеофайла через browser-native preview path.'))
      cleanup()
    }

    video.src = objectUrl
  })
}
