import * as UTIF from 'utif2'

export interface ViewerMetadataItem {
  label: string
  value: string
}

export interface ViewerBinaryPreview {
  bytes: Uint8Array
  mimeType: string
  metadata: ViewerMetadataItem[]
  previewLabel: string
}

interface ExifTagLike {
  computed?: unknown
  description?: unknown
  value?: unknown
}

type ExifReaderModule = {
  load: (
    input: ArrayBuffer,
    options?: Record<string, unknown>,
  ) => Promise<Record<string, ExifTagLike>>
}

type HeicConvert = (input: {
  blob: Blob
  toType?: string
  quality?: number
  multiple?: true
  gifInterval?: number
}) => Promise<Blob | Blob[]>

const metadataTagMap = [
  ['FileType', 'Тип файла'],
  ['Image Width', 'Ширина'],
  ['Image Height', 'Высота'],
  ['Make', 'Камера'],
  ['Model', 'Модель'],
  ['LensModel', 'Объектив'],
  ['DateTimeOriginal', 'Снято'],
  ['Orientation', 'Ориентация'],
  ['ISO', 'ISO'],
  ['ExposureTime', 'Выдержка'],
  ['FNumber', 'Диафрагма'],
  ['FocalLength', 'Фокусное расстояние'],
  ['ColorSpace', 'Цветовое пространство'],
] as const

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

export async function loadStructuredMetadata(buffer: ArrayBuffer): Promise<ViewerMetadataItem[]> {
  const module = (await import('exifreader')) as unknown as ExifReaderModule
  const tags = await module.load(buffer, { async: true, computed: true })
  const metadata: ViewerMetadataItem[] = []

  for (const [tagName, label] of metadataTagMap) {
    const tag = tags[tagName]
    if (!tag) {
      continue
    }

    const value = formatExifValue(tag)
    if (!value) {
      continue
    }

    metadata.push({ label, value })
  }

  return metadata
}

export async function decodeHeicPreview(buffer: ArrayBuffer): Promise<ViewerBinaryPreview> {
  const module = await import('heic2any')
  const convert = (module.default ?? module) as HeicConvert
  const result = await convert({
    blob: new Blob([buffer], { type: 'image/heic' }),
    toType: 'image/jpeg',
    quality: 0.92,
  })
  const blob = Array.isArray(result) ? result[0] : result

  if (!blob) {
    throw new Error('HEIC adapter не вернул raster preview.')
  }

  const bytes = new Uint8Array(await blob.arrayBuffer())

  return {
    bytes,
    mimeType: 'image/jpeg',
    metadata: await loadStructuredMetadata(buffer),
    previewLabel: 'HEIC decode adapter',
  }
}

export async function decodeTiffPreview(buffer: ArrayBuffer): Promise<ViewerBinaryPreview> {
  const metadata = await loadStructuredMetadata(buffer)
  const raster = await decodeTiffLikePreview(buffer)

  return {
    ...raster,
    metadata,
    previewLabel: 'TIFF decode adapter',
  }
}

export async function decodeRawPreview(buffer: ArrayBuffer): Promise<ViewerBinaryPreview> {
  const raster = await decodeTiffLikePreview(buffer)
  const metadata = extractTiffTagMetadata(buffer)

  return {
    ...raster,
    metadata,
    previewLabel: 'RAW preview extraction',
  }
}

async function decodeTiffLikePreview(
  buffer: ArrayBuffer,
): Promise<Omit<ViewerBinaryPreview, 'metadata' | 'previewLabel'>> {
  const ifds = UTIF.decode(buffer)
  const candidates = [...ifds].sort((left, right) => scoreIfd(right) - scoreIfd(left))

  // Для TIFF/RAW нельзя доверять первому IFD: контейнер нередко хранит рядом
  // и сырые данные, и уже готовый preview. Поэтому перебираем все renderable кандидаты.
  for (const ifd of candidates) {
    try {
      UTIF.decodeImage(buffer, ifd)

      if (!ifd.width || !ifd.height) {
        continue
      }

      const rgba = UTIF.toRGBA8(ifd)
      if (rgba.length !== ifd.width * ifd.height * 4) {
        continue
      }

      return {
        bytes: await rgbaToPngBytes(rgba, ifd.width, ifd.height),
        mimeType: 'image/png',
      }
    } catch {
      continue
    }
  }

  throw new Error('Файл распознан, но в нём не найден renderable preview-слой.')
}

function extractTiffTagMetadata(buffer: ArrayBuffer): ViewerMetadataItem[] {
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

function scoreIfd(ifd: UTIF.IFD): number {
  const width = readTiffNumberTag(ifd, 256) ?? ifd.width ?? 0
  const height = readTiffNumberTag(ifd, 257) ?? ifd.height ?? 0
  const samples = readTiffNumberTag(ifd, 277) ?? 1

  return width * height * samples
}

function readTiffNumberTag(ifd: UTIF.IFD, tagId: number): number | null {
  const rawValue = ifd[`t${tagId}`]

  if (typeof rawValue === 'number') {
    return rawValue
  }

  if (Array.isArray(rawValue) && typeof rawValue[0] === 'number') {
    return rawValue[0]
  }

  return null
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

function formatExifValue(tag: ExifTagLike): string | null {
  const value = tag.computed ?? tag.description ?? tag.value

  if (value == null) {
    return null
  }

  if (Array.isArray(value)) {
    return value.join(', ')
  }

  if (typeof value === 'object') {
    return JSON.stringify(value)
  }

  return String(value)
}

function rgbaToPngBytes(rgba: Uint8Array, width: number, height: number): Promise<Uint8Array> {
  const canvas = document.createElement('canvas')
  canvas.width = width
  canvas.height = height

  const context = canvas.getContext('2d')
  if (!context) {
    throw new Error('Canvas 2D context недоступен для сборки preview.')
  }

  const imageData = new ImageData(new Uint8ClampedArray(rgba), width, height)
  context.putImageData(imageData, 0, 0)

  return new Promise((resolve, reject) => {
    canvas.toBlob(async (blob) => {
      if (!blob) {
        reject(new Error('Не удалось собрать PNG preview из raster buffer.'))
        return
      }

      const arrayBuffer = await blob.arrayBuffer()
      resolve(new Uint8Array(arrayBuffer))
    }, 'image/png')
  })
}
