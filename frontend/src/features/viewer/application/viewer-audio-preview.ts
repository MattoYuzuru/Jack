import type { ViewerFormatDefinition } from '../domain/viewer-registry'
import type { ViewerAudioMetadataPayload, ViewerAudioPreviewPayload } from './viewer-audio'
import { formatViewerAudioDuration } from './viewer-audio-tools'
import {
  computeViewerAudioWaveform,
  estimateViewerAudioBitrateBitsPerSecond,
  formatViewerAudioBitrate,
  formatViewerChannelLayout,
  formatViewerSampleRate,
} from './viewer-audio-tools'
import { inspectViewerAudioMetadata } from './viewer-metadata-client'

interface NativeAudioMetadata {
  durationSeconds: number
}

interface BuildAudioPreviewOptions {
  previewLabel?: string
  warnings?: string[]
  extraSummary?: ViewerAudioPreviewPayload['summary']
  playbackPathLabel?: string
  metadataMimeType?: string
  metadataSource?: File
}

export async function buildNativeAudioPreview(
  file: File,
  format: ViewerFormatDefinition,
): Promise<ViewerAudioPreviewPayload> {
  return buildAudioPreviewFromBlob(file, format, {
    playbackPathLabel: 'Браузерное воспроизведение',
    metadataMimeType: file.type,
    metadataSource: file,
  })
}

export async function buildAudioPreviewFromBlob(
  blob: Blob,
  format: ViewerFormatDefinition,
  options: BuildAudioPreviewOptions = {},
): Promise<ViewerAudioPreviewPayload> {
  const objectUrl = URL.createObjectURL(blob)

  try {
    const baseWarnings = [...(options.warnings ?? [])]
    const [{ payload: metadataPayload, warnings: metadataWarnings }, audioMetadata, waveform] =
      await Promise.all([
        options.metadataSource
          ? safeLoadViewerAudioMetadata(options.metadataSource)
          : Promise.resolve({
              payload: {
                summary: [],
                groups: [],
                artworkDataUrl: null,
                searchableText: '',
                technical: {
                  sampleRate: null,
                  channelCount: null,
                  codec: null,
                  container: null,
                },
              } satisfies ViewerAudioMetadataPayload,
              warnings: ['Не удалось получить исходные метаданные аудио.'],
            }),
        inspectNativeAudio(objectUrl),
        decodeViewerAudioWaveform(blob),
      ])

    baseWarnings.push(...metadataWarnings)

    if (!waveform.length) {
      baseWarnings.push(
        'Не удалось собрать волну сигнала, но воспроизведение и основные данные остаются доступны.',
      )
    }

    const estimatedBitrate = estimateViewerAudioBitrateBitsPerSecond(
      blob.size,
      audioMetadata.durationSeconds,
    )

    const technicalMetadata = {
      mimeType: options.metadataMimeType || blob.type || 'Не определён',
      estimatedBitrateBitsPerSecond: estimatedBitrate,
      sampleRate: metadataPayload.technical.sampleRate,
      channelCount: metadataPayload.technical.channelCount,
      codec: metadataPayload.technical.codec,
      container: metadataPayload.technical.container,
      sizeBytes: blob.size,
    }

    return {
      summary: [
        { label: 'Тип аудио', value: format.label },
        { label: 'Длительность', value: formatViewerAudioDuration(audioMetadata.durationSeconds) },
        {
          label: 'Битрейт',
          value: formatViewerAudioBitrate(technicalMetadata.estimatedBitrateBitsPerSecond),
        },
        {
          label: 'Частота дискретизации',
          value: formatViewerSampleRate(technicalMetadata.sampleRate),
        },
        {
          label: 'Каналы',
          value: formatViewerChannelLayout(technicalMetadata.channelCount),
        },
        {
          label: 'Кодек',
          value: technicalMetadata.codec ?? 'n/a',
        },
        {
          label: 'Режим воспроизведения',
          value: options.playbackPathLabel ?? 'Браузерное воспроизведение',
        },
        ...metadataPayload.summary,
        ...(options.extraSummary ?? []),
      ],
      warnings: deduplicateViewerAudioWarnings(baseWarnings),
      metadataGroups: metadataPayload.groups,
      searchableText: metadataPayload.searchableText,
      artworkDataUrl: metadataPayload.artworkDataUrl,
      layout: {
        mode: 'native',
        objectUrl,
        durationSeconds: audioMetadata.durationSeconds,
        waveform,
        metadata: technicalMetadata,
      },
      previewLabel: options.previewLabel ?? format.statusLabel,
    }
  } catch (error) {
    URL.revokeObjectURL(objectUrl)
    throw error
  }
}

async function safeLoadViewerAudioMetadata(
  file: File,
): Promise<{ payload: ViewerAudioMetadataPayload; warnings: string[] }> {
  try {
    return inspectViewerAudioMetadata(file)
  } catch {
    // Metadata parser не должен валить весь audio preview: теги в этой итерации
    // полезны, но playback и waveform важнее, чем идеальный tag coverage.
    return {
      payload: {
        summary: [],
        groups: [],
        artworkDataUrl: null,
        searchableText: '',
        technical: {
          sampleRate: null,
          channelCount: null,
          codec: null,
          container: null,
        },
      },
      warnings: ['Не удалось извлечь теги из исходного аудиофайла.'],
    }
  }
}

function inspectNativeAudio(objectUrl: string): Promise<NativeAudioMetadata> {
  return new Promise((resolve, reject) => {
    const audio = document.createElement('audio')

    audio.preload = 'metadata'

    const cleanup = () => {
      audio.removeAttribute('src')
      audio.load()
    }

    audio.onloadedmetadata = () => {
      resolve({
        durationSeconds: Number.isFinite(audio.duration) ? audio.duration : 0,
      })
      cleanup()
    }

    audio.onerror = () => {
      reject(new Error('Не удалось прочитать данные аудиофайла для просмотра.'))
      cleanup()
    }

    audio.src = objectUrl
  })
}

async function decodeViewerAudioWaveform(blob: Blob): Promise<number[]> {
  const AudioContextConstructor =
    window.AudioContext ||
    (window as Window & { webkitAudioContext?: typeof AudioContext }).webkitAudioContext

  if (!AudioContextConstructor) {
    return []
  }

  const audioContext = new AudioContextConstructor()

  try {
    const sourceBuffer = await blob.arrayBuffer()
    const decodedBuffer = await audioContext.decodeAudioData(sourceBuffer.slice(0))
    return computeViewerAudioWaveform(decodedBuffer)
  } catch {
    return []
  } finally {
    await audioContext.close()
  }
}

function deduplicateViewerAudioWarnings(warnings: string[]): string[] {
  return Array.from(new Set(warnings.filter(Boolean)))
}
