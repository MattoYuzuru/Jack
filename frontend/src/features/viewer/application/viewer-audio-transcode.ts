import { FFmpeg } from '@ffmpeg/ffmpeg'
import type { ViewerFormatDefinition } from '../domain/viewer-registry'
import type { ViewerAudioPreviewPayload } from './viewer-audio'
import {
  createViewerFfmpegSessionId,
  loadViewerFfmpeg,
  normalizeViewerFfmpegBinary,
  safeDeleteViewerFfmpegFile,
} from './viewer-ffmpeg'
import { buildAudioPreviewFromBlob } from './viewer-audio-preview'

interface ViewerLegacyAudioProfile {
  id: string
  runtimeLabel: string
  outputFileName: string
  outputMimeType: string
  args: string[]
  warnings: string[]
}

const LEGACY_AUDIO_TIMEOUT_MS = 180_000

export async function buildLegacyAudioPreview(
  file: File,
  format: ViewerFormatDefinition,
): Promise<ViewerAudioPreviewPayload> {
  const ffmpeg = await loadViewerFfmpeg()
  const sessionId = createViewerFfmpegSessionId('audio')
  const inputPath = `/${sessionId}.${format.extension}`

  await ffmpeg.writeFile(inputPath, new Uint8Array(await file.arrayBuffer()))

  try {
    const profileResult = await transcodeViewerLegacyAudio(ffmpeg, inputPath, format, sessionId)

    return buildAudioPreviewFromBlob(profileResult.blob, format, {
      previewLabel: 'Legacy audio bridge',
      playbackPathLabel: profileResult.playbackPathLabel,
      metadataMimeType: file.type || profileResult.blob.type,
      metadataSource: file,
      warnings: [
        'Legacy audio preview собирается через client-side transcode bridge. Для длинных lossless-треков первый запуск может занять больше времени, чем browser-native path.',
        ...profileResult.warnings,
      ],
      extraSummary: [{ label: 'Runtime Container', value: profileResult.runtimeLabel }],
    })
  } finally {
    await safeDeleteViewerFfmpegFile(ffmpeg, inputPath)
  }
}

async function transcodeViewerLegacyAudio(
  ffmpeg: FFmpeg,
  inputPath: string,
  format: ViewerFormatDefinition,
  sessionId: string,
): Promise<{
  blob: Blob
  runtimeLabel: string
  playbackPathLabel: string
  warnings: string[]
}> {
  const profiles = createViewerLegacyAudioProfiles(sessionId)
  const failures: string[] = []

  for (const profile of profiles) {
    try {
      const blob = await executeViewerLegacyAudioProfile(ffmpeg, inputPath, profile)

      return {
        blob,
        runtimeLabel: profile.runtimeLabel,
        playbackPathLabel: `FFmpeg bridge · ${profile.runtimeLabel}`,
        warnings: [
          `${format.label} был приведён к ${profile.runtimeLabel}, чтобы viewer смог отдать его в browser-native audio element без отдельной playback-ветки.`,
          ...profile.warnings,
        ],
      }
    } catch (error) {
      failures.push(error instanceof Error ? error.message : `Профиль ${profile.id} завершился ошибкой.`)
    }
  }

  throw new Error(
    `Не удалось собрать browser-playable preview для ${format.label}. FFmpeg bridge попробовал несколько профилей, но ни один не завершился успешно. ${failures.join(' ')}`,
  )
}

async function executeViewerLegacyAudioProfile(
  ffmpeg: FFmpeg,
  inputPath: string,
  profile: ViewerLegacyAudioProfile,
): Promise<Blob> {
  try {
    const exitCode = await ffmpeg.exec(
      ['-i', inputPath, ...profile.args, profile.outputFileName],
      LEGACY_AUDIO_TIMEOUT_MS,
    )

    if (exitCode !== 0) {
      throw new Error(`FFmpeg profile ${profile.id} завершился с кодом ${exitCode}.`)
    }

    const data = await ffmpeg.readFile(profile.outputFileName)
    return new Blob([normalizeViewerFfmpegBinary(data)], { type: profile.outputMimeType })
  } finally {
    await safeDeleteViewerFfmpegFile(ffmpeg, profile.outputFileName)
  }
}

function createViewerLegacyAudioProfiles(sessionId: string): ViewerLegacyAudioProfile[] {
  return [
    {
      id: 'mp3-stereo',
      runtimeLabel: 'MP3 transcode',
      outputFileName: `/${sessionId}.mp3`,
      outputMimeType: 'audio/mpeg',
      args: ['-map', '0:a:0', '-vn', '-c:a', 'libmp3lame', '-b:a', '192k'],
      warnings: [],
    },
    {
      id: 'wav-fallback',
      runtimeLabel: 'WAV fallback',
      outputFileName: `/${sessionId}.wav`,
      outputMimeType: 'audio/wav',
      args: ['-map', '0:a:0', '-vn', '-c:a', 'pcm_s16le', '-ar', '44100', '-ac', '2'],
      warnings: [
        'Playback собран через PCM fallback, поэтому preview-blob может получиться заметно тяжелее исходного контейнера.',
      ],
    },
  ]
}
