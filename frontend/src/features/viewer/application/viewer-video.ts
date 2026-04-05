export interface ViewerVideoFact {
  label: string
  value: string
}

export interface ViewerVideoMetadata {
  mimeType: string
  aspectRatio: string
  orientation: string
  estimatedBitrateBitsPerSecond: number | null
  sizeBytes: number
}

export type ViewerVideoLayout = {
  mode: 'native'
  objectUrl: string
  durationSeconds: number
  width: number
  height: number
  metadata: ViewerVideoMetadata
}

export interface ViewerVideoPreviewPayload {
  summary: ViewerVideoFact[]
  warnings: string[]
  layout: ViewerVideoLayout
  previewLabel: string
}

export function formatViewerVideoDuration(durationSeconds: number): string {
  if (!Number.isFinite(durationSeconds) || durationSeconds < 0) {
    return '00:00'
  }

  const roundedSeconds = Math.floor(durationSeconds)
  const hours = Math.floor(roundedSeconds / 3600)
  const minutes = Math.floor((roundedSeconds % 3600) / 60)
  const seconds = roundedSeconds % 60

  if (hours > 0) {
    return `${String(hours).padStart(2, '0')}:${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
  }

  return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
}
