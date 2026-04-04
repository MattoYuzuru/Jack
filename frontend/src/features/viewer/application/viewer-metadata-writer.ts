import piexif from 'piexifjs'
import { detectFileExtension } from '../domain/viewer-registry'
import type { ViewerEditableMetadata } from './viewer-metadata'

export interface ViewerMetadataExportResult {
  blob: Blob
  fileName: string
  mode: 'embedded-jpeg' | 'json-sidecar'
}

interface PiexifTagCollection {
  [tagId: number]: unknown
}

export function canEmbedMetadata(fileName: string): boolean {
  const extension = detectFileExtension(fileName)
  return extension === 'jpg' || extension === 'jpeg'
}

export function formatInputDateForExif(value: string): string {
  const normalizedValue = value.trim()
  if (!normalizedValue) {
    return ''
  }

  const date = new Date(normalizedValue)
  if (Number.isNaN(date.getTime())) {
    return ''
  }

  const year = String(date.getFullYear()).padStart(4, '0')
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hour = String(date.getHours()).padStart(2, '0')
  const minute = String(date.getMinutes()).padStart(2, '0')
  const second = String(date.getSeconds()).padStart(2, '0')

  return `${year}:${month}:${day} ${hour}:${minute}:${second}`
}

export async function exportViewerMetadata(
  file: File,
  metadata: ViewerEditableMetadata,
): Promise<ViewerMetadataExportResult> {
  return canEmbedMetadata(file.name)
    ? writeEmbeddedJpegMetadata(file, metadata)
    : buildMetadataSidecar(file.name, metadata)
}

async function writeEmbeddedJpegMetadata(
  file: File,
  metadata: ViewerEditableMetadata,
): Promise<ViewerMetadataExportResult> {
  const dataUrl = await fileToDataUrl(file)
  const strippedImage = piexif.remove(dataUrl)
  const exifDict = piexif.load(strippedImage)
  const imageTags = (exifDict['0th'] ??= {}) as PiexifTagCollection
  const exifTags = (exifDict.Exif ??= {}) as PiexifTagCollection

  // Пишем только common-поля, которые реально стабильно живут в JPEG EXIF,
  // а не делаем вид, что можем безопасно редактировать весь контейнер.
  mutateTag(imageTags, piexif.ImageIFD.ImageDescription, metadata.description)
  mutateTag(imageTags, piexif.ImageIFD.Artist, metadata.artist)
  mutateTag(imageTags, piexif.ImageIFD.Copyright, metadata.copyright)
  mutateTag(imageTags, piexif.ImageIFD.Software, 'Jack Viewer')
  mutateTag(exifTags, piexif.ExifIFD.DateTimeOriginal, formatInputDateForExif(metadata.capturedAt))

  const nextExif = piexif.dump(exifDict)
  const nextDataUrl = piexif.insert(nextExif, strippedImage)

  return {
    blob: dataUrlToBlob(nextDataUrl),
    fileName: withSuffix(file.name, '-metadata'),
    mode: 'embedded-jpeg',
  }
}

async function buildMetadataSidecar(
  fileName: string,
  metadata: ViewerEditableMetadata,
): Promise<ViewerMetadataExportResult> {
  const payload = {
    fileName,
    exportedAt: new Date().toISOString(),
    metadata,
  }

  return {
    blob: new Blob([JSON.stringify(payload, null, 2)], { type: 'application/json' }),
    fileName: withJsonSuffix(fileName),
    mode: 'json-sidecar',
  }
}

function mutateTag(target: Record<number, unknown>, tagId: number, value: string) {
  const normalizedValue = value.trim()

  if (!normalizedValue) {
    delete target[tagId]
    return
  }

  target[tagId] = normalizedValue
}

function withSuffix(fileName: string, suffix: string): string {
  const dotIndex = fileName.lastIndexOf('.')
  if (dotIndex < 0) {
    return `${fileName}${suffix}`
  }

  return `${fileName.slice(0, dotIndex)}${suffix}${fileName.slice(dotIndex)}`
}

export function withJsonSuffix(fileName: string): string {
  return `${fileName}.jack-metadata.json`
}

function fileToDataUrl(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()

    reader.onload = () => {
      const value = reader.result
      if (typeof value !== 'string') {
        reject(new Error('Не удалось преобразовать файл в Data URL для EXIF-правки.'))
        return
      }

      resolve(value)
    }

    reader.onerror = () => {
      reject(new Error('Не удалось прочитать файл для EXIF-правки.'))
    }

    reader.readAsDataURL(file)
  })
}

function dataUrlToBlob(dataUrl: string): Blob {
  const [header = '', base64 = ''] = dataUrl.split(',')
  const mimeType = header.match(/data:(.*?);base64/u)?.[1] ?? 'application/octet-stream'
  const bytes = Uint8Array.from(atob(base64), (character) => character.charCodeAt(0))

  return new Blob([bytes.buffer], { type: mimeType })
}
