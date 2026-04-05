import { FFmpeg } from '@ffmpeg/ffmpeg'
import type { FileData } from '@ffmpeg/ffmpeg'
import ffmpegCoreUrl from '@ffmpeg/core?url'
import ffmpegWasmUrl from '@ffmpeg/core/wasm?url'
import type { ViewerFormatDefinition } from '../domain/viewer-registry'
import type { ViewerVideoPreviewPayload } from './viewer-video'
import { buildVideoPreviewFromBlob } from './viewer-video-preview'

interface ViewerLegacyVideoProbe {
  durationSeconds: number | null
  width: number | null
  height: number | null
  codecName: string | null
}

interface ViewerLegacyVideoProfile {
  id: string
  runtimeLabel: string
  outputFileName: string
  outputMimeType: string
  args: string[]
  warnings: string[]
}

const LEGACY_VIDEO_TIMEOUT_MS = 240_000

let ffmpegInstance: FFmpeg | null = null
let ffmpegLoadPromise: Promise<FFmpeg> | null = null

export async function buildLegacyVideoPreview(
  file: File,
  format: ViewerFormatDefinition,
): Promise<ViewerVideoPreviewPayload> {
  const ffmpeg = await loadViewerFfmpeg()
  const sessionId = createViewerLegacySessionId()
  const inputPath = `/${sessionId}.${format.extension}`
  const probePath = `/${sessionId}.probe.json`

  await ffmpeg.writeFile(inputPath, new Uint8Array(await file.arrayBuffer()))

  try {
    const sourceProbe = await probeViewerLegacyVideo(ffmpeg, inputPath, probePath)
    const profileResult = await transcodeViewerLegacyVideo(ffmpeg, inputPath, format, sessionId)

    return buildVideoPreviewFromBlob(profileResult.blob, format, {
      previewLabel: 'Legacy decode bridge',
      playbackPathLabel: profileResult.playbackPathLabel,
      metadataMimeType: file.type || profileResult.blob.type,
      warnings: [
        'Legacy video preview собирается через client-side transcode bridge. Для больших файлов первый запуск может занимать заметно больше времени, чем browser-native path.',
        ...profileResult.warnings,
      ],
      extraSummary: [
        { label: 'Runtime Container', value: profileResult.runtimeLabel },
        {
          label: 'Source Codec',
          value: sourceProbe.codecName ?? 'Не удалось извлечь через ffprobe',
        },
        {
          label: 'Source Frame',
          value:
            sourceProbe.width && sourceProbe.height
              ? `${sourceProbe.width} x ${sourceProbe.height}`
              : 'Не удалось извлечь через ffprobe',
        },
      ],
    })
  } finally {
    await safeDeleteViewerLegacyFile(ffmpeg, inputPath)
    await safeDeleteViewerLegacyFile(ffmpeg, probePath)
  }
}

async function transcodeViewerLegacyVideo(
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
  const profiles = createViewerLegacyProfiles(sessionId)
  const failures: string[] = []

  for (const profile of profiles) {
    try {
      const blob = await executeViewerLegacyProfile(ffmpeg, inputPath, profile)

      return {
        blob,
        runtimeLabel: profile.runtimeLabel,
        playbackPathLabel: `FFmpeg bridge · ${profile.runtimeLabel}`,
        warnings: [
          `${format.label} был приведён к ${profile.runtimeLabel}, чтобы viewer смог отдать файл в browser-native video element без отдельной ветки UI.`,
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

async function executeViewerLegacyProfile(
  ffmpeg: FFmpeg,
  inputPath: string,
  profile: ViewerLegacyVideoProfile,
): Promise<Blob> {
  try {
    const exitCode = await ffmpeg.exec(
      ['-i', inputPath, ...profile.args, profile.outputFileName],
      LEGACY_VIDEO_TIMEOUT_MS,
    )

    if (exitCode !== 0) {
      throw new Error(`FFmpeg profile ${profile.id} завершился с кодом ${exitCode}.`)
    }

    const data = await ffmpeg.readFile(profile.outputFileName)
    return new Blob([normalizeViewerLegacyBinary(data)], { type: profile.outputMimeType })
  } finally {
    await safeDeleteViewerLegacyFile(ffmpeg, profile.outputFileName)
  }
}

async function probeViewerLegacyVideo(
  ffmpeg: FFmpeg,
  inputPath: string,
  probePath: string,
): Promise<ViewerLegacyVideoProbe> {
  const exitCode = await ffmpeg.ffprobe(
    [
      '-v',
      'error',
      '-select_streams',
      'v:0',
      '-show_entries',
      'stream=width,height,codec_name',
      '-show_entries',
      'format=duration',
      '-of',
      'json',
      inputPath,
      '-o',
      probePath,
    ],
    20_000,
  )

  if (exitCode !== 0) {
    return {
      durationSeconds: null,
      width: null,
      height: null,
      codecName: null,
    }
  }

  const rawProbe = await ffmpeg.readFile(probePath, 'utf8')
  const probe = JSON.parse(normalizeViewerLegacyText(rawProbe)) as {
    format?: { duration?: string }
    streams?: Array<{ width?: number; height?: number; codec_name?: string }>
  }
  const primaryStream = probe.streams?.[0]

  return {
    durationSeconds: probe.format?.duration ? Number(probe.format.duration) : null,
    width: primaryStream?.width ?? null,
    height: primaryStream?.height ?? null,
    codecName: primaryStream?.codec_name ?? null,
  }
}

async function loadViewerFfmpeg(): Promise<FFmpeg> {
  if (ffmpegInstance?.loaded) {
    return ffmpegInstance
  }

  if (!ffmpegLoadPromise) {
    ffmpegLoadPromise = (async () => {
      const ffmpeg = new FFmpeg()
      await ffmpeg.load({
        coreURL: ffmpegCoreUrl,
        wasmURL: ffmpegWasmUrl,
      })
      ffmpegInstance = ffmpeg
      return ffmpeg
    })().catch((error) => {
      ffmpegLoadPromise = null
      throw error
    })
  }

  return ffmpegLoadPromise
}

function createViewerLegacyProfiles(sessionId: string): ViewerLegacyVideoProfile[] {
  return [
    {
      id: 'mp4-av',
      runtimeLabel: 'MP4 transcode',
      outputFileName: `/${sessionId}.mp4`,
      outputMimeType: 'video/mp4',
      args: [
        '-map',
        '0:v:0',
        '-map',
        '0:a?',
        '-sn',
        '-vf',
        'scale=trunc(iw/2)*2:trunc(ih/2)*2',
        '-c:v',
        'libx264',
        '-preset',
        'ultrafast',
        '-pix_fmt',
        'yuv420p',
        '-movflags',
        '+faststart',
        '-c:a',
        'aac',
        '-b:a',
        '160k',
      ],
      warnings: [],
    },
    {
      id: 'mp4-video-only',
      runtimeLabel: 'MP4 transcode (video only)',
      outputFileName: `/${sessionId}.silent.mp4`,
      outputMimeType: 'video/mp4',
      args: [
        '-map',
        '0:v:0',
        '-sn',
        '-an',
        '-vf',
        'scale=trunc(iw/2)*2:trunc(ih/2)*2',
        '-c:v',
        'libx264',
        '-preset',
        'ultrafast',
        '-pix_fmt',
        'yuv420p',
        '-movflags',
        '+faststart',
      ],
      warnings: [
        'Аудиодорожка не была сохранена в preview-output, потому что совместимый AV-path для этого файла не собрался в браузерном ffmpeg bridge.',
      ],
    },
    {
      id: 'webm-fallback',
      runtimeLabel: 'WebM fallback transcode',
      outputFileName: `/${sessionId}.webm`,
      outputMimeType: 'video/webm',
      args: [
        '-map',
        '0:v:0',
        '-map',
        '0:a?',
        '-sn',
        '-c:v',
        'libvpx-vp9',
        '-row-mt',
        '1',
        '-deadline',
        'realtime',
        '-cpu-used',
        '8',
        '-b:v',
        '0',
        '-crf',
        '32',
        '-c:a',
        'libopus',
        '-b:a',
        '128k',
      ],
      warnings: [
        'MP4 transcode не собрался, поэтому viewer переключился на WebM fallback path.',
      ],
    },
  ]
}

async function safeDeleteViewerLegacyFile(ffmpeg: FFmpeg, path: string) {
  try {
    await ffmpeg.deleteFile(path)
  } catch {
    // FFmpeg MEMFS может не содержать файл после неуспешного профиля, и это не
    // должно ломать viewer cleanup-path.
  }
}

function normalizeViewerLegacyBinary(data: FileData): ArrayBuffer {
  if (typeof data === 'string') {
    return new TextEncoder().encode(data).buffer
  }

  const copy = new Uint8Array(data.byteLength)
  copy.set(data)
  return copy.buffer
}

function normalizeViewerLegacyText(data: FileData): string {
  if (typeof data === 'string') {
    return data
  }

  return new TextDecoder().decode(data)
}

function createViewerLegacySessionId(): string {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) {
    return crypto.randomUUID()
  }

  return `legacy-${Date.now()}`
}
