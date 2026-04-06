import type { ViewerMetadataGroup, ViewerMetadataItem } from './viewer-metadata'

export interface ViewerAudioFact {
  label: string
  value: string
}

export interface ViewerAudioTechnicalMetadata {
  mimeType: string
  estimatedBitrateBitsPerSecond: number | null
  sampleRate: number | null
  channelCount: number | null
  codec: string | null
  container: string | null
  sizeBytes: number
}

export interface ViewerAudioMetadataPayload {
  summary: ViewerMetadataItem[]
  groups: ViewerMetadataGroup[]
  artworkDataUrl: string | null
  searchableText: string
  technical: Omit<
    ViewerAudioTechnicalMetadata,
    'mimeType' | 'estimatedBitrateBitsPerSecond' | 'sizeBytes'
  >
}

export type ViewerAudioLayout = {
  mode: 'native'
  objectUrl: string
  durationSeconds: number
  waveform: number[]
  metadata: ViewerAudioTechnicalMetadata
}

export interface ViewerAudioPreviewPayload {
  summary: ViewerAudioFact[]
  warnings: string[]
  metadataGroups: ViewerMetadataGroup[]
  searchableText: string
  artworkDataUrl: string | null
  layout: ViewerAudioLayout
  previewLabel: string
}
