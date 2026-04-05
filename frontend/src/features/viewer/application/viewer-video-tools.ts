import { formatViewerVideoDuration } from './viewer-video'

export type ViewerSubtitleFormat = 'vtt' | 'srt'

export interface ViewerPreparedSubtitleFile {
  format: ViewerSubtitleFormat
  label: string
  language: string
  cueCount: number
  text: string
  blob: Blob
}

export interface ViewerVideoPosterSnapshot {
  blob: Blob
  fileName: string
  timeSeconds: number
  timeLabel: string
  width: number
  height: number
}

const COMMON_ASPECT_RATIOS = [
  { value: 1, label: '1:1' },
  { value: 4 / 3, label: '4:3' },
  { value: 3 / 2, label: '3:2' },
  { value: 16 / 10, label: '16:10' },
  { value: 16 / 9, label: '16:9' },
  { value: 21 / 9, label: '21:9' },
  { value: 9 / 16, label: '9:16' },
]

export function formatViewerAspectRatio(width: number, height: number): string {
  if (width <= 0 || height <= 0) {
    return 'Unknown'
  }

  const ratio = width / height
  const commonMatch = COMMON_ASPECT_RATIOS.find((entry) => Math.abs(entry.value - ratio) <= 0.03)

  if (commonMatch) {
    return commonMatch.label
  }

  const divisor = greatestCommonDivisor(width, height)
  return `${Math.round(width / divisor)}:${Math.round(height / divisor)}`
}

export function estimateViewerVideoBitrateBitsPerSecond(
  fileSizeBytes: number,
  durationSeconds: number,
): number | null {
  if (fileSizeBytes <= 0 || durationSeconds <= 0 || !Number.isFinite(durationSeconds)) {
    return null
  }

  return Math.round((fileSizeBytes * 8) / durationSeconds)
}

export function formatViewerVideoBitrate(bitsPerSecond: number | null): string {
  if (!bitsPerSecond || bitsPerSecond <= 0 || !Number.isFinite(bitsPerSecond)) {
    return 'n/a'
  }

  if (bitsPerSecond >= 1_000_000) {
    return `${(bitsPerSecond / 1_000_000).toFixed(2)} Mbps`
  }

  if (bitsPerSecond >= 1_000) {
    return `${Math.round(bitsPerSecond / 1_000)} kbps`
  }

  return `${bitsPerSecond} bps`
}

export function resolveViewerVideoOrientation(width: number, height: number): string {
  if (width <= 0 || height <= 0) {
    return 'Unknown'
  }

  if (width === height) {
    return 'Square'
  }

  return width > height ? 'Landscape' : 'Portrait'
}

export function getViewerVideoFrameStepSeconds(frameRate: number): number {
  if (!Number.isFinite(frameRate) || frameRate <= 0) {
    return Number((1 / 24).toFixed(4))
  }

  return Number((1 / frameRate).toFixed(4))
}

export function inferViewerSubtitleFormat(
  fileName: string,
  mimeType = '',
): ViewerSubtitleFormat | null {
  const normalizedName = fileName.toLowerCase()
  const normalizedMime = mimeType.toLowerCase()

  if (normalizedName.endsWith('.vtt') || normalizedMime.includes('text/vtt')) {
    return 'vtt'
  }

  if (
    normalizedName.endsWith('.srt') ||
    normalizedMime.includes('subrip') ||
    normalizedMime.includes('x-subrip')
  ) {
    return 'srt'
  }

  return null
}

export function inferViewerSubtitleLanguage(fileName: string): string {
  const segments = fileName.toLowerCase().split('.')
  const languageCandidate = segments.find((segment) => /^[a-z]{2}(?:-[a-z]{2})?$/.test(segment))

  return languageCandidate ?? 'und'
}

export function convertViewerSrtToVtt(source: string): string {
  const normalizedSource = normalizeViewerText(source)
  const blocks = normalizedSource.split(/\n{2,}/).filter(Boolean)
  const convertedBlocks = blocks.map((block) => {
    const lines = block.split('\n')
    const firstContentIndex = /^\d+$/.test(lines[0] ?? '') ? 1 : 0
    const cueLines = lines.slice(firstContentIndex)

    if (!cueLines.length) {
      return ''
    }

    const [timingLine, ...payloadLines] = cueLines
    if (!timingLine || !timingLine.includes('-->')) {
      return cueLines.join('\n')
    }

    // Браузерный <track> понимает только WebVTT, поэтому SRT приводим к VTT
    // заранее и нормализуем запятые в timecode до точки.
    return [timingLine.replace(/,/g, '.'), ...payloadLines].join('\n')
  })

  return `WEBVTT\n\n${convertedBlocks.filter(Boolean).join('\n\n').trim()}\n`
}

export function ensureViewerWebVtt(source: string): string {
  const normalizedSource = normalizeViewerText(source)

  if (normalizedSource.startsWith('WEBVTT')) {
    return `${normalizedSource.trim()}\n`
  }

  return `WEBVTT\n\n${normalizedSource.trim()}\n`
}

export function countViewerSubtitleCues(
  source: string,
  format: ViewerSubtitleFormat,
): number {
  const normalizedSource = format === 'srt' ? convertViewerSrtToVtt(source) : ensureViewerWebVtt(source)
  const matches = normalizedSource.match(
    /^\s*(?:\d{2}:)?\d{2}:\d{2}\.\d{3}\s+-->\s+(?:\d{2}:)?\d{2}:\d{2}\.\d{3}.*$/gm,
  )

  return matches?.length ?? 0
}

export async function prepareViewerSubtitleFile(file: File): Promise<ViewerPreparedSubtitleFile> {
  const format = inferViewerSubtitleFormat(file.name, file.type)
  if (!format) {
    throw new Error('Поддерживаются только `.vtt` и `.srt` subtitle sidecar файлы.')
  }

  const rawText = await file.text()
  const text = format === 'srt' ? convertViewerSrtToVtt(rawText) : ensureViewerWebVtt(rawText)

  return {
    format,
    label: sanitizeViewerSubtitleLabel(file.name),
    language: inferViewerSubtitleLanguage(file.name),
    cueCount: countViewerSubtitleCues(rawText, format),
    text,
    blob: new Blob([text], { type: 'text/vtt;charset=utf-8' }),
  }
}

export async function captureViewerVideoPoster(
  videoElement: HTMLVideoElement,
  sourceFileName: string,
): Promise<ViewerVideoPosterSnapshot> {
  if (!videoElement.videoWidth || !videoElement.videoHeight) {
    throw new Error('Видео ещё не отдало размеры кадра для poster capture.')
  }

  const canvas = document.createElement('canvas')
  canvas.width = videoElement.videoWidth
  canvas.height = videoElement.videoHeight

  const context = canvas.getContext('2d')
  if (!context) {
    throw new Error('Canvas context недоступен для захвата постера.')
  }

  context.drawImage(videoElement, 0, 0, canvas.width, canvas.height)

  const blob = await canvasToBlob(canvas)
  const safeTimeSeconds = Number.isFinite(videoElement.currentTime) ? videoElement.currentTime : 0

  return {
    blob,
    fileName: buildViewerPosterFileName(sourceFileName, safeTimeSeconds),
    timeSeconds: safeTimeSeconds,
    timeLabel: formatViewerVideoDuration(safeTimeSeconds),
    width: canvas.width,
    height: canvas.height,
  }
}

function buildViewerPosterFileName(sourceFileName: string, timeSeconds: number): string {
  const extensionIndex = sourceFileName.lastIndexOf('.')
  const baseName = extensionIndex > 0 ? sourceFileName.slice(0, extensionIndex) : sourceFileName

  return `${baseName}.poster-${formatPosterTimeToken(timeSeconds)}.png`
}

function formatPosterTimeToken(timeSeconds: number): string {
  const safeSeconds = Math.max(timeSeconds, 0)
  const wholeSeconds = Math.floor(safeSeconds)
  const milliseconds = Math.round((safeSeconds - wholeSeconds) * 1000)
  const hours = Math.floor(wholeSeconds / 3600)
  const minutes = Math.floor((wholeSeconds % 3600) / 60)
  const seconds = wholeSeconds % 60

  return [
    String(hours).padStart(2, '0'),
    String(minutes).padStart(2, '0'),
    String(seconds).padStart(2, '0'),
    String(milliseconds).padStart(3, '0'),
  ].join('-')
}

function sanitizeViewerSubtitleLabel(fileName: string): string {
  return fileName.replace(/\.(vtt|srt)$/i, '')
}

function normalizeViewerText(source: string): string {
  return source.replace(/^\uFEFF/, '').replace(/\r\n/g, '\n').trim()
}

function greatestCommonDivisor(a: number, b: number): number {
  let left = Math.abs(a)
  let right = Math.abs(b)

  while (right !== 0) {
    const remainder = left % right
    left = right
    right = remainder
  }

  return left || 1
}

function canvasToBlob(canvas: HTMLCanvasElement): Promise<Blob> {
  return new Promise((resolve, reject) => {
    canvas.toBlob((blob) => {
      if (!blob) {
        reject(new Error('Не удалось собрать PNG из текущего кадра.'))
        return
      }

      resolve(blob)
    }, 'image/png')
  })
}
