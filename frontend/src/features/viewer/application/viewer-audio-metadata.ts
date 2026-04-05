import { parseBlob, selectCover, type IAudioMetadata } from 'music-metadata-browser'
import type { ViewerMetadataGroup, ViewerMetadataItem } from './viewer-metadata'
import type { ViewerAudioMetadataPayload } from './viewer-audio'
import {
  formatViewerAudioBitrate,
  formatViewerChannelLayout,
  formatViewerAudioDuration,
  formatViewerSampleRate,
} from './viewer-audio-tools'

export async function loadViewerAudioMetadata(file: Blob): Promise<ViewerAudioMetadataPayload> {
  const metadata = await parseBlob(file, {
    skipCovers: false,
    duration: true,
  })

  return {
    summary: buildViewerAudioSummary(metadata),
    groups: buildViewerAudioGroups(metadata),
    artworkDataUrl: buildViewerAudioArtwork(metadata),
    searchableText: buildViewerAudioSearchableText(metadata),
    technical: {
      sampleRate: metadata.format.sampleRate ?? null,
      channelCount: metadata.format.numberOfChannels ?? null,
      codec: metadata.format.codec ?? null,
      container: metadata.format.container ?? null,
    },
  }
}

function buildViewerAudioSummary(metadata: IAudioMetadata): ViewerMetadataItem[] {
  const items: ViewerMetadataItem[] = []

  pushViewerAudioItem(items, 'Title', metadata.common.title)
  pushViewerAudioItem(items, 'Artist', metadata.common.artist)
  pushViewerAudioItem(items, 'Album', metadata.common.album)
  pushViewerAudioItem(
    items,
    'Bitrate',
    metadata.format.bitrate ? formatViewerAudioBitrate(metadata.format.bitrate) : null,
  )
  pushViewerAudioItem(items, 'Sample Rate', formatViewerSampleRate(metadata.format.sampleRate ?? null))
  pushViewerAudioItem(
    items,
    'Channels',
    formatViewerChannelLayout(metadata.format.numberOfChannels ?? null),
  )
  pushViewerAudioItem(items, 'Codec', metadata.format.codec)
  pushViewerAudioItem(items, 'Container', metadata.format.container)

  return items
}

function buildViewerAudioGroups(metadata: IAudioMetadata): ViewerMetadataGroup[] {
  const groups: ViewerMetadataGroup[] = []

  const commonEntries = [
    toViewerAudioItem('Title', metadata.common.title),
    toViewerAudioItem('Artist', metadata.common.artist),
    toViewerAudioItem('Album Artist', metadata.common.albumartist),
    toViewerAudioItem('Album', metadata.common.album),
    toViewerAudioItem('Genre', metadata.common.genre?.join(', ')),
    toViewerAudioItem('Year', metadata.common.year),
    toViewerAudioItem('Track', metadata.common.track.no),
    toViewerAudioItem('Disc', metadata.common.disk.no),
    toViewerAudioItem('Composer', metadata.common.composer?.join(', ')),
    toViewerAudioItem('Comment', metadata.common.comment?.join(' · ')),
  ].filter((item): item is ViewerMetadataItem => item !== null)

  if (commonEntries.length) {
    groups.push({
      id: 'common',
      label: 'Common',
      entries: commonEntries,
    })
  }

  const formatEntries = [
    toViewerAudioItem(
      'Duration',
      metadata.format.duration ? formatViewerAudioDuration(metadata.format.duration) : null,
    ),
    toViewerAudioItem(
      'Bitrate',
      metadata.format.bitrate ? formatViewerAudioBitrate(metadata.format.bitrate) : null,
    ),
    toViewerAudioItem('Sample Rate', formatViewerSampleRate(metadata.format.sampleRate ?? null)),
    toViewerAudioItem('Channels', formatViewerChannelLayout(metadata.format.numberOfChannels ?? null)),
    toViewerAudioItem('Codec', metadata.format.codec),
    toViewerAudioItem('Container', metadata.format.container),
    toViewerAudioItem('Lossless', metadata.format.lossless),
    toViewerAudioItem('Tag Types', metadata.format.tagTypes?.join(', ')),
  ].filter((item): item is ViewerMetadataItem => item !== null)

  if (formatEntries.length) {
    groups.push({
      id: 'format',
      label: 'Format',
      entries: formatEntries,
    })
  }

  const nativeGroups = Object.entries(metadata.native)
    .map(([tagType, nativeGroup], nativeGroupIndex) => {
      const entries = nativeGroup
        .map((entry) => toViewerAudioItem(entry.id, formatViewerAudioValue(entry.value)))
        .filter((item): item is ViewerMetadataItem => item !== null)

      if (!entries.length) {
        return null
      }

      return {
        id: `native-${nativeGroupIndex}`,
        label: `Native Tags · ${tagType}`,
        entries,
      } satisfies ViewerMetadataGroup
    })
    .filter((group): group is ViewerMetadataGroup => group !== null)

  groups.push(...nativeGroups)

  return groups
}

function buildViewerAudioArtwork(metadata: IAudioMetadata): string | null {
  const cover = selectCover(metadata.common.picture)
  if (!cover) {
    return null
  }

  return `data:${cover.format};base64,${uint8ArrayToBase64(cover.data)}`
}

function buildViewerAudioSearchableText(metadata: IAudioMetadata): string {
  return [
    metadata.common.title,
    metadata.common.artist,
    metadata.common.albumartist,
    metadata.common.album,
    metadata.common.genre?.join(' '),
    metadata.common.comment?.join(' '),
    metadata.common.composer?.join(' '),
    metadata.format.codec,
    metadata.format.container,
  ]
    .filter(Boolean)
    .join(' ')
}

function pushViewerAudioItem(items: ViewerMetadataItem[], label: string, value: unknown) {
  const item = toViewerAudioItem(label, value)
  if (item) {
    items.push(item)
  }
}

function toViewerAudioItem(label: string, value: unknown): ViewerMetadataItem | null {
  const formattedValue = formatViewerAudioValue(value)
  if (!formattedValue || formattedValue === 'n/a') {
    return null
  }

  return {
    label,
    value: formattedValue,
  }
}

function formatViewerAudioValue(value: unknown): string | null {
  if (value == null) {
    return null
  }

  if (typeof value === 'string') {
    const normalizedValue = value.trim()
    return normalizedValue ? normalizedValue : null
  }

  if (typeof value === 'number' || typeof value === 'boolean') {
    return String(value)
  }

  if (Array.isArray(value)) {
    return value.map((entry) => formatViewerAudioValue(entry)).filter(Boolean).join(', ')
  }

  if (typeof value === 'object') {
    return JSON.stringify(value)
  }

  return null
}

function uint8ArrayToBase64(bytes: Uint8Array): string {
  let value = ''

  for (const byte of bytes) {
    value += String.fromCharCode(byte)
  }

  return btoa(value)
}
