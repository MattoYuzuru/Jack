import * as UTIF from 'utif2'
import {
  createEmptyMetadataPayload,
  loadViewerMetadataPayload,
  type ViewerMetadataItem,
  type ViewerMetadataPayload,
} from './viewer-metadata'

export interface ViewerBinaryPreview {
  bytes: Uint8Array
  mimeType: string
  metadata: ViewerMetadataPayload
  previewLabel: string
}

const tiffRawTagMap = [
  [271, 'Камера'],
  [272, 'Модель'],
  [306, 'Дата'],
  [274, 'Ориентация'],
  [34855, 'ISO'],
  [33434, 'Выдержка'],
  [33437, 'Диафрагма'],
  [37386, 'Фокусное расстояние'],
  [42036, 'Объектив'],
] as const

export async function loadStructuredMetadata(buffer: ArrayBuffer): Promise<ViewerMetadataPayload> {
  try {
    return await loadViewerMetadataPayload(buffer)
  } catch {
    return createEmptyMetadataPayload()
  }
}

export function extractRawFallbackMetadata(buffer: ArrayBuffer): ViewerMetadataItem[] {
  const ifd = UTIF.decode(buffer)[0]
  if (!ifd) {
    return []
  }
  const metadata: ViewerMetadataItem[] = []

  for (const [tagId, label] of tiffRawTagMap) {
    const rawValue = ifd[`t${tagId}`]
    const value = formatTiffTagValue(tagId, rawValue)
    if (!value) {
      continue
    }

    metadata.push({ label, value })
  }

  return metadata
}

function formatTiffTagValue(tagId: number, rawValue: unknown): string | null {
  if (rawValue == null) {
    return null
  }

  if (tagId === 274 && Array.isArray(rawValue) && typeof rawValue[0] === 'number') {
    return describeOrientation(rawValue[0])
  }

  if (Array.isArray(rawValue)) {
    if (rawValue.length === 1) {
      return String(rawValue[0])
    }

    if (rawValue.length === 2 && rawValue.every((value) => typeof value === 'number')) {
      const numerator = rawValue[0]
      const denominator = rawValue[1]
      if (typeof numerator === 'number' && typeof denominator === 'number' && denominator) {
        if (tagId === 33434) {
          return `${numerator}/${denominator} sec`
        }

        return String(Number((numerator / denominator).toFixed(2)))
      }
    }

    return rawValue.join(', ')
  }

  if (rawValue instanceof Uint8Array) {
    return `${rawValue.byteLength} bytes`
  }

  return String(rawValue)
}

function describeOrientation(value: number): string {
  const map: Record<number, string> = {
    1: 'Normal',
    2: 'Mirror horizontal',
    3: 'Rotate 180',
    4: 'Mirror vertical',
    5: 'Mirror horizontal + rotate 270',
    6: 'Rotate 90',
    7: 'Mirror horizontal + rotate 90',
    8: 'Rotate 270',
  }

  return map[value] ?? String(value)
}
