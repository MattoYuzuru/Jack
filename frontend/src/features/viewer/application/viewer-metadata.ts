import type { ExpandedTags, Tags } from 'exifreader'

export interface ViewerMetadataItem {
  label: string
  value: string
}

export interface ViewerMetadataGroup {
  id: string
  label: string
  entries: ViewerMetadataItem[]
}

export interface ViewerEditableMetadata {
  description: string
  artist: string
  copyright: string
  capturedAt: string
}

export interface ViewerMetadataPayload {
  summary: ViewerMetadataItem[]
  groups: ViewerMetadataGroup[]
  editable: ViewerEditableMetadata
  thumbnailDataUrl: string | null
}

interface ExifReaderModule {
  load(input: ArrayBuffer, options?: Record<string, unknown>): Promise<Tags | ExpandedTags>
}

const summaryQueries = [
  { label: 'Тип файла', groupOrder: ['file'], tagName: 'FileType' },
  { label: 'Ширина', groupOrder: ['file', 'exif'], tagName: 'Image Width' },
  { label: 'Высота', groupOrder: ['file', 'exif'], tagName: 'Image Height' },
  { label: 'Камера', groupOrder: ['exif'], tagName: 'Make' },
  { label: 'Модель', groupOrder: ['exif'], tagName: 'Model' },
  { label: 'Объектив', groupOrder: ['exif', 'makerNotes'], tagName: 'LensModel' },
  { label: 'Снято', groupOrder: ['exif'], tagName: 'DateTimeOriginal' },
  { label: 'Ориентация', groupOrder: ['exif'], tagName: 'Orientation' },
  { label: 'ISO', groupOrder: ['exif'], tagName: 'ISO' },
  { label: 'Выдержка', groupOrder: ['exif'], tagName: 'ExposureTime' },
  { label: 'Диафрагма', groupOrder: ['exif'], tagName: 'FNumber' },
  { label: 'Фокусное расстояние', groupOrder: ['exif'], tagName: 'FocalLength' },
  { label: 'Цветовое пространство', groupOrder: ['icc', 'exif'], tagName: 'ColorSpace' },
  { label: 'ICC profile', groupOrder: ['icc'], tagName: 'Profile Description' },
] as const

const groupLabelMap: Record<string, string> = {
  file: 'File',
  exif: 'EXIF',
  gps: 'GPS',
  icc: 'ICC Profile',
  composite: 'Composite',
  xmp: 'XMP',
  makerNotes: 'Maker Notes',
  photoshop: 'Photoshop',
  Thumbnail: 'Thumbnail',
}

export function createEmptyEditableMetadata(): ViewerEditableMetadata {
  return {
    description: '',
    artist: '',
    copyright: '',
    capturedAt: '',
  }
}

export function createEmptyMetadataPayload(): ViewerMetadataPayload {
  return {
    summary: [],
    groups: [],
    editable: createEmptyEditableMetadata(),
    thumbnailDataUrl: null,
  }
}

export async function loadViewerMetadataPayload(buffer: ArrayBuffer): Promise<ViewerMetadataPayload> {
  const module = (await import('exifreader')) as unknown as ExifReaderModule
  const expanded = (await module.load(buffer, {
    async: true,
    computed: true,
    expanded: true,
  })) as ExpandedTags

  return {
    summary: buildMetadataSummary(expanded),
    groups: buildMetadataGroups(expanded),
    editable: buildEditableMetadata(expanded),
    thumbnailDataUrl: expanded.Thumbnail?.base64
      ? `data:image/jpeg;base64,${expanded.Thumbnail.base64}`
      : null,
  }
}

export function mergeMetadataPayload(
  base: ViewerMetadataPayload,
  injectedSummary: ViewerMetadataItem[],
  injectedGroups: ViewerMetadataGroup[] = [],
): ViewerMetadataPayload {
  const summaryMap = new Map(base.summary.map((item) => [item.label, item.value]))
  for (const item of injectedSummary) {
    summaryMap.set(item.label, item.value)
  }

  const groups = [...base.groups]
  for (const group of injectedGroups) {
    const index = groups.findIndex((existingGroup) => existingGroup.id === group.id)
    if (index >= 0) {
      groups[index] = group
    } else {
      groups.push(group)
    }
  }

  return {
    ...base,
    summary: Array.from(summaryMap.entries()).map(([label, value]) => ({ label, value })),
    groups,
  }
}

export function formatExifDateForInput(value: string): string {
  const match = value.match(
    /^(?<year>\d{4}):(?<month>\d{2}):(?<day>\d{2}) (?<hour>\d{2}):(?<minute>\d{2})(?::\d{2})?$/u,
  )

  if (!match?.groups) {
    return ''
  }

  return `${match.groups.year}-${match.groups.month}-${match.groups.day}T${match.groups.hour}:${match.groups.minute}`
}

function buildMetadataSummary(expanded: ExpandedTags): ViewerMetadataItem[] {
  const items: ViewerMetadataItem[] = []

  for (const query of summaryQueries) {
    const value = findTagValue(expanded, query.groupOrder, query.tagName)
    if (!value) {
      continue
    }

    items.push({
      label: query.label,
      value,
    })
  }

  return items
}

function buildMetadataGroups(expanded: ExpandedTags): ViewerMetadataGroup[] {
  const orderedGroupIds = [
    ...Object.keys(groupLabelMap),
    ...Object.keys(expanded).filter((groupId) => !(groupId in groupLabelMap)),
  ]
  const groups: ViewerMetadataGroup[] = []

  for (const groupId of orderedGroupIds) {
    const rawGroup = expanded[groupId as keyof ExpandedTags]
    if (!rawGroup || typeof rawGroup !== 'object') {
      continue
    }

    const entries = Object.entries(rawGroup)
      .filter(([tagName]) => tagName !== '_raw' && tagName !== 'image' && tagName !== 'base64')
      .map(([tagName, rawValue]) => {
        const value = formatUnknownTagValue(rawValue)
        if (!value) {
          return null
        }

        return {
          label: tagName,
          value,
        }
      })
      .filter((item): item is ViewerMetadataItem => item !== null)

    if (!entries.length) {
      continue
    }

    groups.push({
      id: groupId,
      label: groupLabelMap[groupId] ?? groupId,
      entries,
    })
  }

  return groups
}

function buildEditableMetadata(expanded: ExpandedTags): ViewerEditableMetadata {
  return {
    description: findTagValue(expanded, ['exif'], 'ImageDescription') ?? '',
    artist: findTagValue(expanded, ['exif'], 'Artist') ?? '',
    copyright: findTagValue(expanded, ['exif'], 'Copyright') ?? '',
    capturedAt: formatExifDateForInput(
      findTagValue(expanded, ['exif'], 'DateTimeOriginal') ??
        findTagValue(expanded, ['exif'], 'DateTimeDigitized') ??
        '',
    ),
  }
}

function findTagValue(
  expanded: ExpandedTags,
  groupOrder: readonly string[],
  tagName: string,
): string | null {
  for (const groupId of groupOrder) {
    const rawGroup = expanded[groupId as keyof ExpandedTags]
    if (!rawGroup || typeof rawGroup !== 'object') {
      continue
    }

    const maybeTag = (rawGroup as Record<string, unknown>)[tagName]
    const value = formatUnknownTagValue(maybeTag)

    if (value) {
      return value
    }
  }

  return null
}

function formatUnknownTagValue(rawValue: unknown): string | null {
  if (rawValue == null) {
    return null
  }

  if (typeof rawValue === 'string' || typeof rawValue === 'number' || typeof rawValue === 'boolean') {
    return String(rawValue)
  }

  if (Array.isArray(rawValue)) {
    return rawValue.map((value) => String(value)).join(', ')
  }

  if (rawValue instanceof Uint8Array) {
    return `${rawValue.byteLength} bytes`
  }

  if (typeof rawValue === 'object') {
    const tag = rawValue as {
      description?: unknown
      computed?: unknown
      value?: unknown
    }

    const structuredValue = tag.computed ?? tag.description ?? tag.value

    if (structuredValue == null) {
      return null
    }

    if (Array.isArray(structuredValue)) {
      return structuredValue.map((value) => String(value)).join(', ')
    }

    if (structuredValue instanceof Uint8Array) {
      return `${structuredValue.byteLength} bytes`
    }

    if (typeof structuredValue === 'object') {
      return JSON.stringify(structuredValue)
    }

    return String(structuredValue)
  }

  return null
}
